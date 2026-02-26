#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOG_DIR="${LOG_DIR:-/tmp/gateway-jwt-verify-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$LOG_DIR"

GW_PORT="${GW_PORT:-18173}"
CHAT_PORT="${CHAT_PORT:-18093}"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/.gradle-codex}"

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
  local retries="${4:-120}"
  local code=""
  for _ in $(seq 1 "$retries"); do
    code=$(curl -sS -o /dev/null -w "%{http_code}" --max-time 2 "$url" || true)
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

b64url() {
  printf '%s' "$1" | openssl base64 -A | tr '+/' '-_' | tr -d '='
}

if [[ ! -f "$ROOT/docker/.env" ]]; then
  echo "[ERR] docker/.env not found at $ROOT/docker/.env" >&2
  exit 1
fi

SIGNING_KEY="$(sed -n 's/^APP_SECURITY_CONTEXT_SIGNING_KEY=//p' "$ROOT/docker/.env" | head -n1)"
MAX_AGE="$(sed -n 's/^APP_SECURITY_CONTEXT_MAX_AGE_MILLIS=//p' "$ROOT/docker/.env" | head -n1)"
if [[ -z "$SIGNING_KEY" ]]; then
  echo "[ERR] APP_SECURITY_CONTEXT_SIGNING_KEY is empty in docker/.env" >&2
  exit 1
fi
if [[ -z "$MAX_AGE" ]]; then
  MAX_AGE=300000
fi

check_port_free "$GW_PORT"
check_port_free "$CHAT_PORT"

echo "[INFO] preparing docker infra"
(cd "$ROOT/docker" && docker compose up -d postgres redis zookeeper kafka mariadb >/dev/null)

echo "[INFO] starting chat-service on port ${CHAT_PORT}"
(
  cd "$ROOT"
  env \
    GRADLE_USER_HOME="$GRADLE_USER_HOME" \
    SERVER_PORT="$CHAT_PORT" \
    APP_SECURITY_CONTEXT_SIGNING_KEY="$SIGNING_KEY" \
    APP_SECURITY_CONTEXT_MAX_AGE_MILLIS="$MAX_AGE" \
    CHAT_SECURITY_GATEWAY_AUTH_ENABLED=true \
    ./gradlew :servers:services:chat:bootRun --no-daemon >"$LOG_DIR/chat.log" 2>&1
) &
PIDS+=("$!")

wait_http_code_prefix "chat-service health" "http://127.0.0.1:${CHAT_PORT}/actuator/health" "200" 150

echo "[INFO] starting client-gateway on port ${GW_PORT}"
(
  cd "$ROOT"
  env \
    GRADLE_USER_HOME="$GRADLE_USER_HOME" \
    SERVER_PORT="$GW_PORT" \
    APP_SECURITY_CONTEXT_SIGNING_KEY="$SIGNING_KEY" \
    APP_SECURITY_CONTEXT_MAX_AGE_MILLIS="$MAX_AGE" \
    APP_SERVICE_CHAT_URL="http://127.0.0.1:${CHAT_PORT}" \
    APP_SERVICE_CHAT_WS_URL="ws://127.0.0.1:${CHAT_PORT}" \
    ./gradlew :servers:gateways:client-gateway:bootRun --no-daemon >"$LOG_DIR/gateway.log" 2>&1
) &
PIDS+=("$!")

# client-gateway에는 actuator가 없어 보호된 chat 엔드포인트 401 응답으로 기동 상태를 확인한다.
wait_http_code_prefix "client-gateway" "http://127.0.0.1:${GW_PORT}/api/v1/chat/rooms?size=1" "401" 150

JWT_HEADER='{"alg":"none","typ":"JWT"}'
JWT_PAYLOAD_OK='{"userId":777,"roles":["buyer","user"]}'
JWT_PAYLOAD_BAD='{"sub":"not-number","roles":["buyer"]}'

TOKEN_OK="$(b64url "$JWT_HEADER").$(b64url "$JWT_PAYLOAD_OK")."
TOKEN_BAD="$(b64url "$JWT_HEADER").$(b64url "$JWT_PAYLOAD_BAD")."

RAW1="$(curl -sS --max-time 10 -w '\n%{http_code}' \
  "http://127.0.0.1:${GW_PORT}/api/v1/chat/rooms?size=1")"
BODY1="$(extract_http_body "$RAW1")"
CODE1="$(extract_http_status "$RAW1")"
echo "$BODY1" >"$LOG_DIR/case1_no_token.json"
assert_status "case1 no token" "$CODE1" "401"

RAW2="$(curl -sS --max-time 10 -w '\n%{http_code}' \
  -H "Authorization: Basic abc" \
  "http://127.0.0.1:${GW_PORT}/api/v1/chat/rooms?size=1")"
BODY2="$(extract_http_body "$RAW2")"
CODE2="$(extract_http_status "$RAW2")"
echo "$BODY2" >"$LOG_DIR/case2_malformed_auth.json"
assert_status "case2 malformed auth" "$CODE2" "401"

RAW3="$(curl -sS --max-time 10 -w '\n%{http_code}' \
  -H "Authorization: Bearer $TOKEN_BAD" \
  -H "X-User-Id: 999" \
  -H "X-User-Roles: ADMIN" \
  "http://127.0.0.1:${GW_PORT}/api/v1/chat/rooms?size=1")"
BODY3="$(extract_http_body "$RAW3")"
CODE3="$(extract_http_status "$RAW3")"
echo "$BODY3" >"$LOG_DIR/case3_bad_claim_spoof.json"
assert_status "case3 bad jwt + spoof" "$CODE3" "401"

RAW4="$(curl -sS --max-time 10 -w '\n%{http_code}' \
  -H "Authorization: Bearer $TOKEN_OK" \
  -H "X-User-Id: not-a-number" \
  -H "X-User-Roles: ADMIN" \
  "http://127.0.0.1:${GW_PORT}/api/v1/chat/rooms?size=1")"
BODY4="$(extract_http_body "$RAW4")"
CODE4="$(extract_http_status "$RAW4")"
echo "$BODY4" >"$LOG_DIR/case4_valid_jwt_spoof_invalid_header.json"
assert_status "case4 valid jwt + spoof invalid header" "$CODE4" "200"

RAW5="$(curl -sS --max-time 10 -w '\n%{http_code}' \
  -H "Authorization: Bearer $TOKEN_OK" \
  -H 'X-User-Id: <script>alert(1)</script>' \
  -H 'X-User-Roles: <img src=x onerror=alert(1)>' \
  "http://127.0.0.1:${GW_PORT}/api/v1/chat/rooms?size=1")"
BODY5="$(extract_http_body "$RAW5")"
CODE5="$(extract_http_status "$RAW5")"
echo "$BODY5" >"$LOG_DIR/case5_valid_jwt_xss_spoof.json"
assert_status "case5 valid jwt + xss spoof" "$CODE5" "200"

echo "[INFO] result passes=${PASSES} failures=${FAILURES}"
if [[ "$FAILURES" -gt 0 ]]; then
  exit 1
fi

pass "gateway JWT relay e2e passed"
