#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOG_DIR="${LOG_DIR:-/tmp/gateway_search_fallback_e2e_logs_$(date +%Y%m%d_%H%M%S)}"
mkdir -p "$LOG_DIR"

GW_PORT="${GW_PORT:-18573}"
SEARCH_PORT="${SEARCH_PORT:-18588}"
MEDIA_PORT="${MEDIA_PORT:-18594}"
ELASTICSEARCH_PORT="${ELASTICSEARCH_PORT:-23173}"

AWS_PROFILE_NAME="${AWS_PROFILE:-mzc}"
AWS_REGION_NAME="${AWS_REGION:-ap-northeast-2}"
MEDIA_BUCKET="${MEDIA_BUCKET:-team2-donmoa-media-raw}"
MEDIA_PREFIX="${MEDIA_PREFIX:-team2-donmoa-media}"
CF_COMMENT="${CF_DISTRIBUTION_COMMENT:-team2-donmoa-media}"
MEDIA_CLOUDFRONT_DOMAIN="${MEDIA_CLOUDFRONT_DOMAIN:-}"

INTERNAL_AUTH_TOKEN="${INTERNAL_AUTH_TOKEN:-gw-search-fallback-token}"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/.gradle-codex}"

PIDS=()
FAILURES=0
PASSES=0

pass() {
  PASSES=$((PASSES + 1))
  echo "[PASS] $1"
}

fail() {
  FAILURES=$((FAILURES + 1))
  echo "[FAIL] $1"
}

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "[ERR] command not found: $cmd" >&2
    exit 1
  fi
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

extract_http_body() {
  local raw="$1"
  printf '%s\n' "$raw" | sed '$d'
}

extract_http_status() {
  local raw="$1"
  printf '%s\n' "$raw" | tail -n1
}

ensure_http_status() {
  local label="$1"
  local actual="$2"
  local expected="$3"
  local body="${4:-}"
  if [[ "$actual" == "$expected" ]]; then
    pass "$label"
    return 0
  fi
  fail "$label (expected=${expected}, actual=${actual})"
  if [[ -n "$body" ]]; then
    echo "[DEBUG] ${label} response body=${body}"
  fi
  return 1
}

ensure_json_success() {
  local label="$1"
  local body="$2"
  local success
  success="$(echo "$body" | jq -r '.success // false')"
  if [[ "$success" == "true" ]]; then
    pass "$label"
    return 0
  fi
  local code message
  code="$(echo "$body" | jq -r '.error.code // "UNKNOWN"')"
  message="$(echo "$body" | jq -r '.error.message // ""')"
  fail "$label (code=${code}, message=${message})"
  return 1
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
      pass "$name health is UP"
      return 0
    fi
    sleep 2
  done
  fail "$name health timeout (port=${port})"
  return 1
}

wait_gateway_ready() {
  for _ in {1..120}; do
    local code
    code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 2 \
      "http://127.0.0.1:${GW_PORT}/bff/v1/search?q=ready-check&size=1" 2>/dev/null || true)"
    if [[ "$code" == "200" ]]; then
      pass "client-gateway ready for /bff/v1/search"
      return 0
    fi
    sleep 2
  done
  fail "client-gateway ready timeout for /bff/v1/search"
  return 1
}

wait_es_up() {
  for _ in {1..90}; do
    local code
    code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 2 "http://127.0.0.1:${ELASTICSEARCH_PORT}/_cluster/health" 2>/dev/null || true)"
    if [[ "$code" == "200" ]]; then
      pass "elasticsearch is reachable"
      return 0
    fi
    sleep 2
  done
  fail "elasticsearch reachability timeout"
  return 1
}

resolve_media_domain() {
  if [[ -n "$MEDIA_CLOUDFRONT_DOMAIN" ]]; then
    echo "$MEDIA_CLOUDFRONT_DOMAIN"
    return 0
  fi

  if command -v aws >/dev/null 2>&1; then
    local domain
    domain="$(env AWS_PROFILE="$AWS_PROFILE_NAME" aws cloudfront list-distributions \
      --query "DistributionList.Items[?Comment=='${CF_COMMENT}'].DomainName | [0]" \
      --output text 2>/dev/null || true)"
    if [[ -n "$domain" && "$domain" != "None" ]]; then
      echo "https://${domain}"
      return 0
    fi
  fi

  echo "https://${MEDIA_BUCKET}.s3.${AWS_REGION_NAME}.amazonaws.com"
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
      APP_GATEWAY_SECURITY_ENABLED=false \
      MEDIA_S3_REGION="$AWS_REGION_NAME" \
      MEDIA_S3_BUCKET="$MEDIA_BUCKET" \
      MEDIA_S3_KEY_PREFIX="$MEDIA_PREFIX" \
      MEDIA_CLOUDFRONT_DOMAIN="$MEDIA_DOMAIN" \
      ./gradlew :servers:services:media-api:bootRun --no-daemon >>"$LOG_DIR/media-api.log" 2>&1
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
      SNOWFLAKE_WORKER_ID=31 \
      APP_GATEWAY_SECURITY_ENABLED=false \
      MEDIA_SERVICE_URL="http://127.0.0.1:${MEDIA_PORT}" \
      ELASTICSEARCH_URIS="http://127.0.0.1:${ELASTICSEARCH_PORT}" \
      ./gradlew :servers:services:search:bootRun --no-daemon >>"$LOG_DIR/search.log" 2>&1
  ) &
  PIDS+=("$!")
}

start_gateway() {
  echo "[INFO] starting client-gateway on ${GW_PORT}"
  (
    cd "$ROOT"
    env \
      GRADLE_USER_HOME="$GRADLE_USER_HOME" \
      SERVER_PORT="$GW_PORT" \
      APP_SERVICE_SEARCH_URL="http://127.0.0.1:${SEARCH_PORT}" \
      APP_SERVICE_MEDIA_URL="http://127.0.0.1:${MEDIA_PORT}" \
      GATEWAY_SECURITY_INTERNAL_AUTH_TOKEN="$INTERNAL_AUTH_TOKEN" \
      ./gradlew :servers:gateways:client-gateway:bootRun --no-daemon >>"$LOG_DIR/gateway.log" 2>&1
  ) &
  PIDS+=("$!")
}

create_media_and_confirm() {
  local result_var="$1"
  local label="$2"
  local tmp_file
  tmp_file="$(mktemp)"
  local png_base64
  png_base64='iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/aWQAAAAASUVORK5CYII='
  printf '%s' "$png_base64" | openssl base64 -d -A >"$tmp_file"
  local file_size
  file_size="$(wc -c <"$tmp_file" | tr -d ' ')"

  local intent_raw intent_body intent_status
  intent_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${MEDIA_PORT}/api/v1/media/upload-intents" \
    -H "Content-Type: application/json" \
    -d "{\"fileName\":\"${label}.png\",\"contentType\":\"image/png\",\"fileSize\":${file_size}}")"
  intent_body="$(extract_http_body "$intent_raw")"
  intent_status="$(extract_http_status "$intent_raw")"
  ensure_http_status "${label} upload-intent status" "$intent_status" "200" "$intent_body" || return 1
  ensure_json_success "${label} upload-intent success" "$intent_body" || return 1

  local media_id presigned_url upload_token
  media_id="$(echo "$intent_body" | jq -r '.data.mediaId // empty')"
  presigned_url="$(echo "$intent_body" | jq -r '.data.presignedUrl // empty')"
  upload_token="$(echo "$intent_body" | jq -r '.data.uploadToken // empty')"
  if [[ -z "$media_id" || -z "$presigned_url" || -z "$upload_token" ]]; then
    fail "${label} upload-intent response missing fields"
    return 1
  fi

  local put_status
  put_status="$(curl -sS -o /dev/null -w '%{http_code}' -X PUT "$presigned_url" \
    -H "Content-Type: image/png" \
    --data-binary @"$tmp_file")"
  if [[ "$put_status" == "200" || "$put_status" == "204" ]]; then
    pass "${label} presigned upload"
  else
    fail "${label} presigned upload failed (status=${put_status})"
    return 1
  fi

  local confirm_raw confirm_body confirm_status
  confirm_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${MEDIA_PORT}/api/v1/media/confirm" \
    -H "Content-Type: application/json" \
    -d "{\"mediaId\":${media_id},\"uploadToken\":\"${upload_token}\"}")"
  confirm_body="$(extract_http_body "$confirm_raw")"
  confirm_status="$(extract_http_status "$confirm_raw")"
  ensure_http_status "${label} confirm status" "$confirm_status" "200" "$confirm_body" || return 1
  ensure_json_success "${label} confirm success" "$confirm_body" || return 1

  rm -f "$tmp_file"
  printf -v "$result_var" '%s' "$media_id"
}

recreate_search_index() {
  local raw body status
  raw="$(curl -sS -w '\n%{http_code}' -X POST \
    "http://127.0.0.1:${SEARCH_PORT}/internal/v1/search/indexes/recreate?indexName=items")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "search index recreate status" "$status" "200" "$body" || return 1
  ensure_json_success "search index recreate success" "$body" || return 1
}

index_item_document() {
  local item_id="$1"
  local media_id="$2"
  local keyword="$3"
  local payload
  payload="$(cat <<JSON
{
  "itemId": ${item_id},
  "title": "${keyword}",
  "description": "gateway search fallback e2e",
  "category": "TEST",
  "domainType": "PRODUCT",
  "price": 10000,
  "status": "ON_SALE",
  "stock": 10,
  "thumbnailMediaId": ${media_id},
  "thumbnailUrlSnapshot": null,
  "mediaVersion": 1,
  "stockVersion": 1,
  "tags": ["fallback", "e2e"],
  "createdAt": "2026-02-24T00:00:00Z"
}
JSON
)"

  local index_raw index_body index_status
  index_raw="$(curl -sS -w '\n%{http_code}' -X PUT \
    "http://127.0.0.1:${ELASTICSEARCH_PORT}/items-write/_doc/${item_id}?refresh=true" \
    -H "Content-Type: application/json" \
    -d "$payload")"
  index_body="$(extract_http_body "$index_raw")"
  index_status="$(extract_http_status "$index_raw")"
  if [[ "$index_status" == "200" || "$index_status" == "201" ]]; then
    pass "index seed document inserted (itemId=${item_id})"
  else
    fail "index seed document insert failed (status=${index_status})"
    echo "[DEBUG] index seed response body=${index_body}"
    return 1
  fi
}

query_search() {
  local base_url="$1"
  local keyword="$2"
  curl -sS "${base_url}?q=${keyword}&size=20"
}

wait_search_item_visible() {
  local base_url="$1"
  local keyword="$2"
  local expected_item_id="$3"
  local label="$4"

  for _ in {1..30}; do
    local body item_id
    body="$(query_search "$base_url" "$keyword")"
    item_id="$(echo "$body" | jq -r '.data.items[0].itemId // empty' 2>/dev/null || true)"
    if [[ "$item_id" == "$expected_item_id" ]]; then
      pass "$label"
      return 0
    fi
    sleep 1
  done
  fail "$label (itemId=${expected_item_id})"
  return 1
}

require_cmd curl
require_cmd jq
require_cmd docker
require_cmd lsof
require_cmd openssl
require_cmd mktemp
require_cmd wc

check_port_free "$GW_PORT"
check_port_free "$SEARCH_PORT"
check_port_free "$MEDIA_PORT"

echo "[INFO] preparing infra"
(cd "$ROOT/docker" && docker compose up -d postgres redis zookeeper kafka mariadb elasticsearch >/dev/null)
wait_es_up

MEDIA_DOMAIN="$(resolve_media_domain)"
echo "[INFO] media public domain: ${MEDIA_DOMAIN}"

start_media
start_search
start_gateway

wait_health "media-api" "$MEDIA_PORT"
wait_health "search-service" "$SEARCH_PORT"
wait_gateway_ready

created_media_id=""
keyword="fallback-e2e-$(date +%s)"
item_id="$((2000000000 + RANDOM))"

create_media_and_confirm created_media_id "fallback-e2e-media"
if [[ -z "$created_media_id" || ! "$created_media_id" =~ ^[0-9]+$ ]]; then
  fail "created mediaId missing or invalid"
  exit 1
fi
recreate_search_index
index_item_document "$item_id" "$created_media_id" "$keyword"
wait_search_item_visible "http://127.0.0.1:${SEARCH_PORT}/api/v1/search" "$keyword" "$item_id" "seed item is visible in direct search"

direct_body="$(query_search "http://127.0.0.1:${SEARCH_PORT}/api/v1/search" "$keyword")"
ensure_json_success "direct search success" "$direct_body"
direct_thumbnail_url="$(echo "$direct_body" | jq -r '.data.items[0].thumbnailUrl // empty')"
direct_media_id="$(echo "$direct_body" | jq -r '.data.items[0].thumbnailMediaId // empty')"
if [[ "$direct_media_id" == "$created_media_id" ]]; then
  pass "direct search contains thumbnailMediaId"
else
  fail "direct search thumbnailMediaId mismatch (expected=${created_media_id}, actual=${direct_media_id})"
fi
if [[ -z "$direct_thumbnail_url" ]]; then
  pass "direct search thumbnailUrl is empty before fallback"
else
  fail "direct search thumbnailUrl should be empty before fallback (actual=${direct_thumbnail_url})"
fi

bff_body="$(query_search "http://127.0.0.1:${GW_PORT}/bff/v1/search" "$keyword")"
ensure_json_success "bff search success" "$bff_body"
bff_thumbnail_url="$(echo "$bff_body" | jq -r '.data.items[0].thumbnailUrl // empty')"
bff_media_id="$(echo "$bff_body" | jq -r '.data.items[0].thumbnailMediaId // empty')"
if [[ "$bff_media_id" == "$created_media_id" ]]; then
  pass "bff search contains thumbnailMediaId"
else
  fail "bff search thumbnailMediaId mismatch (expected=${created_media_id}, actual=${bff_media_id})"
fi
if [[ -n "$bff_thumbnail_url" ]]; then
  pass "bff search thumbnailUrl fallback resolved"
else
  fail "bff search thumbnailUrl fallback unresolved"
fi

echo "[INFO] summary: passes=${PASSES}, failures=${FAILURES}"
if [[ "$FAILURES" -gt 0 ]]; then
  exit 1
fi
pass "gateway search fallback e2e verify passed"
