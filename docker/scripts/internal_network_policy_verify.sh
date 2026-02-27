#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOG_DIR="${LOG_DIR:-/tmp/internal_network_policy_logs_$(date +%Y%m%d_%H%M%S)}"
mkdir -p "$LOG_DIR"

PRODUCT_PORT="${PRODUCT_PORT:-18484}"
SEARCH_PORT="${SEARCH_PORT:-18488}"
MEDIA_PORT="${MEDIA_PORT:-18494}"
ELASTICSEARCH_PORT="${ELASTICSEARCH_PORT:-23173}"

GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/.gradle-codex}"
GATEWAY_TOKEN="${GATEWAY_TOKEN:-internal-network-e2e-token}"

AWS_PROFILE_NAME="${AWS_PROFILE:-mzc}"
AWS_REGION_NAME="${AWS_REGION:-ap-northeast-2}"
MEDIA_BUCKET="${MEDIA_BUCKET:-team2-donmoa-media-raw}"
MEDIA_PREFIX="${MEDIA_PREFIX:-team2-donmoa-media}"
MEDIA_CLOUDFRONT_DOMAIN="${MEDIA_CLOUDFRONT_DOMAIN:-}"
MEDIA_DOMAIN="${MEDIA_CLOUDFRONT_DOMAIN:-https://${MEDIA_BUCKET}.s3.${AWS_REGION_NAME}.amazonaws.com}"

PIDS=()
PASSES=0
FAILURES=0

pass() {
  PASSES=$((PASSES + 1))
  echo "[PASS] $1"
}

fail() {
  FAILURES=$((FAILURES + 1))
  echo "[FAIL] $1"
}

cleanup() {
  for pid in "${PIDS[@]:-}"; do
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
      wait "$pid" 2>/dev/null || true
    fi
  done
  sleep 1
  for pid in "${PIDS[@]:-}"; do
    if kill -0 "$pid" 2>/dev/null; then
      kill -9 "$pid" 2>/dev/null || true
      wait "$pid" 2>/dev/null || true
    fi
  done
  echo "[INFO] logs: $LOG_DIR"
}
trap cleanup EXIT

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "[ERR] required command not found: $cmd" >&2
    exit 1
  fi
}

check_port_free() {
  local port="$1"
  if lsof -tiTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    fail "port ${port} is already in use"
    return 1
  fi
  pass "port ${port} is free"
}

wait_health() {
  local name="$1"
  local port="$2"
  for _ in {1..120}; do
    local status
    status="$(curl -sS --max-time 2 "http://127.0.0.1:${port}/actuator/health" | jq -r '.status' 2>/dev/null || true)"
    if [[ "$status" == "UP" ]]; then
      pass "${name} health is UP"
      return 0
    fi
    sleep 2
  done
  fail "${name} health timeout (port=${port})"
  return 1
}

extract_http_body() {
  local raw="$1"
  printf '%s\n' "$raw" | sed '$d'
}

extract_http_status() {
  local raw="$1"
  printf '%s\n' "$raw" | tail -n1
}

assert_status() {
  local label="$1"
  local actual="$2"
  local expected="$3"
  if [[ "$actual" == "$expected" ]]; then
    pass "${label} status=${actual}"
  else
    fail "${label} expected=${expected} actual=${actual}"
  fi
}

assert_not_status() {
  local label="$1"
  local actual="$2"
  local blocked="$3"
  if [[ "$actual" != "$blocked" ]]; then
    pass "${label} status=${actual} (not ${blocked})"
  else
    fail "${label} expected!=${blocked} actual=${actual}"
  fi
}

start_media() {
  echo "[INFO] starting media-api on ${MEDIA_PORT}"
  (
    cd "$ROOT"
    env \
      AWS_PROFILE="$AWS_PROFILE_NAME" \
      AWS_REGION="$AWS_REGION_NAME" \
      GRADLE_USER_HOME="$GRADLE_USER_HOME" \
      SERVER_PORT="$MEDIA_PORT" \
      APP_GATEWAY_SECURITY_ENABLED=true \
      APP_GATEWAY_SECURITY_GATEWAY_AUTH_ENABLED=true \
      APP_GATEWAY_SECURITY_INTERNAL_AUTH_TOKEN="$GATEWAY_TOKEN" \
      MEDIA_S3_REGION="$AWS_REGION_NAME" \
      MEDIA_S3_BUCKET="$MEDIA_BUCKET" \
      MEDIA_S3_KEY_PREFIX="$MEDIA_PREFIX" \
      MEDIA_CLOUDFRONT_DOMAIN="$MEDIA_DOMAIN" \
      ./gradlew :servers:services:media-api:bootRun --no-daemon >>"$LOG_DIR/media-api.log" 2>&1
  ) &
  PIDS+=("$!")
}

start_product() {
  echo "[INFO] starting product-service on ${PRODUCT_PORT}"
  (
    cd "$ROOT"
    env \
      GRADLE_USER_HOME="$GRADLE_USER_HOME" \
      SERVER_PORT="$PRODUCT_PORT" \
      SNOWFLAKE_WORKER_ID=21 \
      APP_GATEWAY_SECURITY_ENABLED=true \
      APP_GATEWAY_SECURITY_GATEWAY_AUTH_ENABLED=true \
      APP_GATEWAY_SECURITY_INTERNAL_AUTH_TOKEN="$GATEWAY_TOKEN" \
      MEDIA_SERVICE_URL="http://127.0.0.1:${MEDIA_PORT}" \
      ./gradlew :servers:services:product:bootRun --no-daemon >>"$LOG_DIR/product.log" 2>&1
  ) &
  PIDS+=("$!")
}

start_search() {
  echo "[INFO] starting search-service on ${SEARCH_PORT}"
  (
    cd "$ROOT"
    env \
      GRADLE_USER_HOME="$GRADLE_USER_HOME" \
      SERVER_PORT="$SEARCH_PORT" \
      SNOWFLAKE_WORKER_ID=22 \
      APP_GATEWAY_SECURITY_ENABLED=true \
      APP_GATEWAY_SECURITY_GATEWAY_AUTH_ENABLED=true \
      APP_GATEWAY_SECURITY_INTERNAL_AUTH_TOKEN="$GATEWAY_TOKEN" \
      ELASTICSEARCH_URIS="http://127.0.0.1:${ELASTICSEARCH_PORT}" \
      MEDIA_SERVICE_URL="http://127.0.0.1:${MEDIA_PORT}" \
      ./gradlew :servers:services:search:bootRun --no-daemon >>"$LOG_DIR/search.log" 2>&1
  ) &
  PIDS+=("$!")
}

verify_media_internal_auth() {
  local raw body status
  raw="$(curl -sS -w '\n%{http_code}' "http://127.0.0.1:${MEDIA_PORT}/internal/v1/media/1/url")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  echo "$body" >"$LOG_DIR/media_internal_no_token.json"
  assert_status "media internal no token" "$status" "401"

  raw="$(curl -sS -w '\n%{http_code}' -H "X-Gateway-Auth: wrong-token" \
    "http://127.0.0.1:${MEDIA_PORT}/internal/v1/media/1/url")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  echo "$body" >"$LOG_DIR/media_internal_wrong_token.json"
  assert_status "media internal wrong token" "$status" "401"

  raw="$(curl -sS -w '\n%{http_code}' -H "X-Gateway-Auth: ${GATEWAY_TOKEN}" \
    "http://127.0.0.1:${MEDIA_PORT}/internal/v1/media/1/url")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  echo "$body" >"$LOG_DIR/media_internal_valid_token.json"
  assert_not_status "media internal valid token" "$status" "401"
}

verify_product_internal_auth() {
  local raw body status
  raw="$(curl -sS -w '\n%{http_code}' "http://127.0.0.1:${PRODUCT_PORT}/internal/v1/items/1")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  echo "$body" >"$LOG_DIR/product_internal_no_token.json"
  assert_status "product internal no token" "$status" "401"

  raw="$(curl -sS -w '\n%{http_code}' -H "X-Gateway-Auth: wrong-token" \
    "http://127.0.0.1:${PRODUCT_PORT}/internal/v1/items/1")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  echo "$body" >"$LOG_DIR/product_internal_wrong_token.json"
  assert_status "product internal wrong token" "$status" "401"

  raw="$(curl -sS -w '\n%{http_code}' -H "X-Gateway-Auth: ${GATEWAY_TOKEN}" \
    "http://127.0.0.1:${PRODUCT_PORT}/internal/v1/items/1")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  echo "$body" >"$LOG_DIR/product_internal_valid_token.json"
  assert_not_status "product internal valid token" "$status" "401"
}

verify_search_internal_auth() {
  local raw body status
  raw="$(curl -sS -w '\n%{http_code}' "http://127.0.0.1:${SEARCH_PORT}/internal/v1/search/popular/blocked-keywords")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  echo "$body" >"$LOG_DIR/search_internal_no_token.json"
  assert_status "search internal no token" "$status" "401"

  raw="$(curl -sS -w '\n%{http_code}' -H "X-Gateway-Auth: wrong-token" \
    "http://127.0.0.1:${SEARCH_PORT}/internal/v1/search/popular/blocked-keywords")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  echo "$body" >"$LOG_DIR/search_internal_wrong_token.json"
  assert_status "search internal wrong token" "$status" "401"

  raw="$(curl -sS -w '\n%{http_code}' -H "X-Gateway-Auth: ${GATEWAY_TOKEN}" \
    "http://127.0.0.1:${SEARCH_PORT}/internal/v1/search/popular/blocked-keywords")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  echo "$body" >"$LOG_DIR/search_internal_valid_token.json"
  assert_status "search internal valid token" "$status" "200"
}

require_cmd curl
require_cmd jq
require_cmd docker
require_cmd lsof

check_port_free "$PRODUCT_PORT"
check_port_free "$SEARCH_PORT"
check_port_free "$MEDIA_PORT"

echo "[INFO] preparing infra"
(cd "$ROOT/docker" && docker compose up -d postgres redis zookeeper kafka mariadb elasticsearch >/dev/null)

start_media
start_product
start_search

wait_health "media-api" "$MEDIA_PORT"
wait_health "product-service" "$PRODUCT_PORT"
wait_health "search-service" "$SEARCH_PORT"

verify_media_internal_auth
verify_product_internal_auth
verify_search_internal_auth

echo "[INFO] result passes=${PASSES} failures=${FAILURES}"
if [[ "$FAILURES" -gt 0 ]]; then
  exit 1
fi

pass "internal network policy verify passed"
