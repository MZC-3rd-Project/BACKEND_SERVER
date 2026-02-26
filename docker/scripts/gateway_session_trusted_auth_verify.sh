#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOG_DIR="${LOG_DIR:-/tmp/gateway-session-trusted-auth-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$LOG_DIR"

GW_PORT="${GW_PORT:-18173}"
CHAT_PORT="${CHAT_PORT:-18093}"
REDIS_CONTAINER="${REDIS_CONTAINER:-project03-redis}"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/.gradle-codex-e2e}"

TEST_USER_ID="${TEST_USER_ID:-777}"
TEST_ROLES="${TEST_ROLES:-USER,BUYER}"
TEST_SID="${TEST_SID:-sid-trusted-e2e-777}"

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
  echo "[INFO] gateway/chat logs: $LOG_DIR"
}
trap cleanup EXIT

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "[ERR] command not found: $cmd" >&2
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

wait_http_code_prefix() {
  local name="$1"
  local url="$2"
  local prefix="$3"
  local retries="${4:-150}"
  local code=""
  for _ in $(seq 1 "$retries"); do
    code="$(curl -sS -o /dev/null -w "%{http_code}" --max-time 2 "$url" || true)"
    if [[ "$code" == "$prefix"* ]]; then
      pass "${name} is ready (${code})"
      return 0
    fi
    sleep 1
  done
  fail "${name} readiness timeout (last=${code})"
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

assert_error_code() {
  local label="$1"
  local body="$2"
  local expected="$3"
  local actual
  actual="$(echo "$body" | jq -r '.error.code // empty')"
  if [[ "$actual" == "$expected" ]]; then
    pass "${label} code=${actual}"
  else
    fail "${label} expected=${expected} actual=${actual}"
  fi
}

b64url() {
  printf '%s' "$1" | openssl base64 -A | tr '+/' '-_' | tr -d '='
}

resolve_signing_key() {
  if [[ -n "${APP_SECURITY_CONTEXT_SIGNING_KEY:-}" ]]; then
    SIGNING_KEY="$APP_SECURITY_CONTEXT_SIGNING_KEY"
    return 0
  fi

  if [[ -f "$ROOT/docker/.env" ]]; then
    SIGNING_KEY="$(sed -n 's/^APP_SECURITY_CONTEXT_SIGNING_KEY=//p' "$ROOT/docker/.env" | head -n1)"
    if [[ -n "$SIGNING_KEY" ]]; then
      return 0
    fi
  fi

  echo "[ERR] APP_SECURITY_CONTEXT_SIGNING_KEY is required (env or docker/.env)" >&2
  exit 1
}

resolve_max_age_millis() {
  if [[ -n "${APP_SECURITY_CONTEXT_MAX_AGE_MILLIS:-}" ]]; then
    MAX_AGE_MILLIS="$APP_SECURITY_CONTEXT_MAX_AGE_MILLIS"
    return 0
  fi

  if [[ -f "$ROOT/docker/.env" ]]; then
    MAX_AGE_MILLIS="$(sed -n 's/^APP_SECURITY_CONTEXT_MAX_AGE_MILLIS=//p' "$ROOT/docker/.env" | head -n1)"
  fi
  if [[ -z "${MAX_AGE_MILLIS:-}" ]]; then
    MAX_AGE_MILLIS="300000"
  fi
}

build_gateway_context() {
  local user_id="$1"
  local roles="$2"
  local nonce="$3"
  local timestamp="$4"
  local payload signature

  payload="${user_id}|${roles}|${nonce}|${timestamp}"
  signature="$(printf '%s' "$payload" | openssl dgst -binary -sha256 -hmac "$SIGNING_KEY" | openssl base64 -A)"

  printf '%s.%s.%s.%s.%s' \
    "$(b64url "$user_id")" \
    "$(b64url "$roles")" \
    "$(b64url "$nonce")" \
    "$timestamp" \
    "$signature"
}

require_cmd docker
require_cmd jq
require_cmd openssl
require_cmd curl
require_cmd lsof

resolve_signing_key
resolve_max_age_millis

check_port_free "$GW_PORT"
check_port_free "$CHAT_PORT"

echo "[INFO] preparing docker infra"
(cd "$ROOT/docker" && docker compose up -d postgres redis zookeeper kafka mariadb >/dev/null)

echo "[INFO] seeding redis sid=${TEST_SID}"
docker exec -i "$REDIS_CONTAINER" redis-cli HSET "gateway:sess:${TEST_SID}" status ACTIVE >/dev/null

echo "[INFO] starting chat-service on port ${CHAT_PORT}"
(
  cd "$ROOT"
  env \
    GRADLE_USER_HOME="$GRADLE_USER_HOME" \
    SERVER_PORT="$CHAT_PORT" \
    APP_SECURITY_CONTEXT_SIGNING_KEY="$SIGNING_KEY" \
    APP_SECURITY_CONTEXT_MAX_AGE_MILLIS="$MAX_AGE_MILLIS" \
    CHAT_SECURITY_GATEWAY_AUTH_ENABLED=true \
    ./gradlew :servers:services:chat:bootRun --no-daemon >"$LOG_DIR/chat.log" 2>&1
) &
PIDS+=("$!")

wait_http_code_prefix "chat-service health" "http://127.0.0.1:${CHAT_PORT}/actuator/health" "200" 150

echo "[INFO] starting client-gateway on port ${GW_PORT} (session + trusted-header pre-auth)"
(
  cd "$ROOT"
  env \
    GRADLE_USER_HOME="$GRADLE_USER_HOME" \
    SERVER_PORT="$GW_PORT" \
    APP_SECURITY_CONTEXT_SIGNING_KEY="$SIGNING_KEY" \
    APP_SECURITY_CONTEXT_MAX_AGE_MILLIS="$MAX_AGE_MILLIS" \
    GATEWAY_SESSION_ENABLED=true \
    GATEWAY_SESSION_TRUSTED_HEADER_AUTH_ENABLED=true \
    APP_SERVICE_CHAT_URL="http://127.0.0.1:${CHAT_PORT}" \
    APP_SERVICE_CHAT_WS_URL="ws://127.0.0.1:${CHAT_PORT}" \
    ./gradlew :servers:gateways:client-gateway:bootRun --no-daemon >"$LOG_DIR/gateway.log" 2>&1
) &
PIDS+=("$!")

wait_http_code_prefix "client-gateway protected chat endpoint" "http://127.0.0.1:${GW_PORT}/api/v1/chat/rooms?size=1" "401" 150

NOW_MILLIS="$(( $(date +%s) * 1000 ))"
NONCE="$(openssl rand -hex 8)"
TRUSTED_CONTEXT="$(build_gateway_context "$TEST_USER_ID" "$TEST_ROLES" "$NONCE" "$NOW_MILLIS")"
BROKEN_CONTEXT="${TRUSTED_CONTEXT}x"

RAW1="$(curl -sS --max-time 10 -w '\n%{http_code}' \
  "http://127.0.0.1:${GW_PORT}/api/v1/chat/rooms?size=1")"
BODY1="$(extract_http_body "$RAW1")"
CODE1="$(extract_http_status "$RAW1")"
echo "$BODY1" >"$LOG_DIR/case1_no_headers.json"
assert_status "case1 no auth headers" "$CODE1" "401"
assert_error_code "case1 no auth headers" "$BODY1" "GW-AUTH-003"

RAW2="$(curl -sS --max-time 10 -w '\n%{http_code}' \
  -H "X-Gateway-Context: ${TRUSTED_CONTEXT}" \
  -H "X-Session-Id: ${TEST_SID}" \
  "http://127.0.0.1:${GW_PORT}/api/v1/chat/rooms?size=1")"
BODY2="$(extract_http_body "$RAW2")"
CODE2="$(extract_http_status "$RAW2")"
echo "$BODY2" >"$LOG_DIR/case2_trusted_context_active_sid.json"
assert_status "case2 trusted context + active sid" "$CODE2" "200"

RAW3="$(curl -sS --max-time 10 -w '\n%{http_code}' \
  -H "X-Gateway-Context: ${TRUSTED_CONTEXT}" \
  -H "X-Session-Id: sid-not-exists" \
  "http://127.0.0.1:${GW_PORT}/api/v1/chat/rooms?size=1")"
BODY3="$(extract_http_body "$RAW3")"
CODE3="$(extract_http_status "$RAW3")"
echo "$BODY3" >"$LOG_DIR/case3_trusted_context_unknown_sid.json"
assert_status "case3 trusted context + unknown sid" "$CODE3" "401"
assert_error_code "case3 trusted context + unknown sid" "$BODY3" "GW-AUTH-006"

RAW4="$(curl -sS --max-time 10 -w '\n%{http_code}' \
  -H "X-Gateway-Context: ${BROKEN_CONTEXT}" \
  -H "X-Session-Id: ${TEST_SID}" \
  "http://127.0.0.1:${GW_PORT}/api/v1/chat/rooms?size=1")"
BODY4="$(extract_http_body "$RAW4")"
CODE4="$(extract_http_status "$RAW4")"
echo "$BODY4" >"$LOG_DIR/case4_broken_signature.json"
assert_status "case4 broken signature" "$CODE4" "401"
assert_error_code "case4 broken signature" "$BODY4" "GW-AUTH-009"

echo "[INFO] result passes=${PASSES} failures=${FAILURES}"
if [[ "$FAILURES" -gt 0 ]]; then
  exit 1
fi

pass "gateway trusted-header session pre-auth e2e passed"
