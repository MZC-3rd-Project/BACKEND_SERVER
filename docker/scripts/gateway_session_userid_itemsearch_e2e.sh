#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOG_DIR="${LOG_DIR:-/tmp/gateway-session-userid-itemsearch-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$LOG_DIR"

GW_PORT="${GW_PORT:-18173}"
PRODUCT_PORT="${PRODUCT_PORT:-18084}"
CHAT_PORT="${CHAT_PORT:-18093}"
REDIS_CONTAINER="${REDIS_CONTAINER:-project03-redis}"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/.gradle-codex-e2e}"

SELLER_ID="${SELLER_ID:-910001}"
BUYER_ID="${BUYER_ID:-920002}"
STORE_ID="${STORE_ID:-930001}"
SELLER_SID="${SELLER_SID:-sid-seller-910001}"
BUYER_SID="${BUYER_SID:-sid-buyer-920002}"

INTERNAL_AUTH_TOKEN="${INTERNAL_AUTH_TOKEN:-gw-session-e2e-token}"

PIDS=()
PASSES=0
FAILURES=0
WARNINGS=0
AUTH_HEADERS=()
SIGNING_KEY=""
MAX_AGE_MILLIS=""

pass() {
  PASSES=$((PASSES + 1))
  echo "[PASS] $1"
}

fail() {
  FAILURES=$((FAILURES + 1))
  echo "[FAIL] $1"
}

warn() {
  WARNINGS=$((WARNINGS + 1))
  echo "[WARN] $1"
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

wait_health() {
  local name="$1"
  local port="$2"
  for _ in {1..150}; do
    local status
    status="$(curl -sS --max-time 2 "http://127.0.0.1:${port}/actuator/health" | jq -r '.status' 2>/dev/null || true)"
    if [[ "$status" == "UP" ]]; then
      pass "${name} health is UP"
      return 0
    fi
    sleep 1
  done
  fail "${name} health timeout"
  return 1
}

wait_gateway_ready() {
  local url="http://127.0.0.1:${GW_PORT}/api/v1/chat/rooms?size=1"
  for _ in {1..150}; do
    local code
    code="$(curl -sS -o /dev/null -w "%{http_code}" --max-time 2 "$url" || true)"
    if [[ "$code" == "401" ]]; then
      pass "client-gateway ready (protected chat path returns 401)"
      return 0
    fi
    sleep 1
  done
  fail "client-gateway ready timeout"
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

ensure_http_status() {
  local label="$1"
  local actual="$2"
  local expected="$3"
  if [[ "$actual" == "$expected" ]]; then
    pass "$label"
  else
    fail "$label (expected=${expected}, actual=${actual})"
  fi
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
  fail "$label (code=$code, message=$message)"
  return 1
}

ensure_error_code() {
  local label="$1"
  local body="$2"
  local expected="$3"
  local code
  code="$(echo "$body" | jq -r '.error.code // empty')"
  if [[ "$code" == "$expected" ]]; then
    pass "$label"
  else
    fail "$label (expected=${expected}, actual=${code})"
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

build_trusted_headers() {
  local user_id="$1"
  local roles="$2"
  local sid="$3"
  local nonce timestamp gateway_context

  nonce="$(openssl rand -hex 8)"
  timestamp="$(( $(date +%s) * 1000 ))"
  gateway_context="$(build_gateway_context "$user_id" "$roles" "$nonce" "$timestamp")"

  AUTH_HEADERS=(
    -H "X-Gateway-Context: ${gateway_context}"
    -H "X-Session-Id: ${sid}"
  )
}

require_cmd curl
require_cmd jq
require_cmd docker
require_cmd openssl
require_cmd lsof

resolve_signing_key
resolve_max_age_millis

check_port_free "$GW_PORT"
check_port_free "$PRODUCT_PORT"
check_port_free "$CHAT_PORT"

echo "[INFO] preparing docker infra"
(cd "$ROOT/docker" && docker compose up -d postgres redis zookeeper kafka >/dev/null)

echo "[INFO] seeding redis session states"
docker exec -i "$REDIS_CONTAINER" redis-cli HSET "gateway:sess:${SELLER_SID}" status ACTIVE >/dev/null
docker exec -i "$REDIS_CONTAINER" redis-cli HSET "gateway:sess:${BUYER_SID}" status ACTIVE >/dev/null
pass "redis seeded active sid for seller/buyer"

echo "[INFO] starting product-service on ${PRODUCT_PORT}"
(
  cd "$ROOT"
  env \
    GRADLE_USER_HOME="$GRADLE_USER_HOME" \
    SERVER_PORT="$PRODUCT_PORT" \
    APP_SECURITY_CONTEXT_SIGNING_KEY="$SIGNING_KEY" \
    APP_SECURITY_CONTEXT_MAX_AGE_MILLIS="$MAX_AGE_MILLIS" \
    APP_GATEWAY_SECURITY_ENABLED=true \
    APP_GATEWAY_SECURITY_INTERNAL_AUTH_TOKEN="$INTERNAL_AUTH_TOKEN" \
    ./gradlew :servers:services:product:bootRun --no-daemon >"$LOG_DIR/product.log" 2>&1
) &
PIDS+=("$!")

echo "[INFO] starting chat-service on ${CHAT_PORT}"
(
  cd "$ROOT"
  env \
    GRADLE_USER_HOME="$GRADLE_USER_HOME" \
    SERVER_PORT="$CHAT_PORT" \
    PRODUCT_SERVICE_URL="http://127.0.0.1:${PRODUCT_PORT}" \
    APP_SECURITY_CONTEXT_SIGNING_KEY="$SIGNING_KEY" \
    APP_SECURITY_CONTEXT_MAX_AGE_MILLIS="$MAX_AGE_MILLIS" \
    APP_GATEWAY_SECURITY_ENABLED=true \
    APP_GATEWAY_SECURITY_INTERNAL_AUTH_TOKEN="$INTERNAL_AUTH_TOKEN" \
    ./gradlew :servers:services:chat:bootRun --no-daemon >"$LOG_DIR/chat.log" 2>&1
) &
PIDS+=("$!")

echo "[INFO] starting client-gateway on ${GW_PORT}"
(
  cd "$ROOT"
  env \
    GRADLE_USER_HOME="$GRADLE_USER_HOME" \
    SERVER_PORT="$GW_PORT" \
    APP_SECURITY_CONTEXT_SIGNING_KEY="$SIGNING_KEY" \
    APP_SECURITY_CONTEXT_MAX_AGE_MILLIS="$MAX_AGE_MILLIS" \
    GATEWAY_SESSION_ENABLED=true \
    GATEWAY_SESSION_TRUSTED_HEADER_AUTH_ENABLED=true \
    GATEWAY_SECURITY_INTERNAL_AUTH_TOKEN="$INTERNAL_AUTH_TOKEN" \
    APP_SERVICE_PRODUCT_URL="http://127.0.0.1:${PRODUCT_PORT}" \
    APP_SERVICE_CHAT_URL="http://127.0.0.1:${CHAT_PORT}" \
    APP_SERVICE_CHAT_WS_URL="ws://127.0.0.1:${CHAT_PORT}" \
    ./gradlew :servers:gateways:client-gateway:bootRun --no-daemon >"$LOG_DIR/gateway.log" 2>&1
) &
PIDS+=("$!")

wait_health "product-service" "$PRODUCT_PORT"
wait_health "chat-service" "$CHAT_PORT"
wait_gateway_ready

raw_no_auth="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${GW_PORT}/bff/v1/products" \
  -H "Content-Type: application/json" \
  -d '{"item":{},"images":[]}')"
body_no_auth="$(extract_http_body "$raw_no_auth")"
status_no_auth="$(extract_http_status "$raw_no_auth")"
ensure_http_status "no-auth bff product create rejected" "$status_no_auth" "401"
ensure_error_code "no-auth bff product create error code" "$body_no_auth" "GW-AUTH-003"

ts="$(date +%s)"
product_payload="$(cat <<JSON
{"title":"Session E2E Product ${ts}","description":"session auth e2e item","price":14500,"storeId":${STORE_ID},"options":[{"optionName":"기본","additionalPrice":0,"stockQuantity":9}],"shippingInfo":{"shippingFee":3000,"freeShippingThreshold":40000,"estimatedDays":2,"returnPolicy":"7일 내 환불"}}
JSON
)"

build_trusted_headers "$SELLER_ID" "SELLER,USER" "$SELLER_SID"
raw_create_product="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${GW_PORT}/bff/v1/products" \
  -H "Content-Type: application/json" \
  "${AUTH_HEADERS[@]}" \
  -d "{\"item\":${product_payload},\"images\":[]}")"
body_create_product="$(extract_http_body "$raw_create_product")"
status_create_product="$(extract_http_status "$raw_create_product")"
ensure_http_status "session-auth product create status" "$status_create_product" "200"
ensure_json_success "session-auth product create success" "$body_create_product" || exit 1

created_product_id="$(echo "$body_create_product" | jq -r '.data.id // empty')"
created_seller_id="$(echo "$body_create_product" | jq -r '.data.sellerId // empty')"
if [[ -z "$created_product_id" || "$created_product_id" == "null" ]]; then
  fail "created product id resolved"
  exit 1
else
  pass "created product id resolved (${created_product_id})"
fi
if [[ "$created_seller_id" == "$SELLER_ID" ]]; then
  pass "product domain uses forwarded X-User-Id (sellerId=${created_seller_id})"
else
  fail "product domain uses forwarded X-User-Id (expected=${SELLER_ID}, actual=${created_seller_id})"
fi

raw_list_before="$(curl -sS -w '\n%{http_code}' "http://127.0.0.1:${GW_PORT}/bff/v1/items?type=PRODUCT&size=100")"
body_list_before="$(extract_http_body "$raw_list_before")"
status_list_before="$(extract_http_status "$raw_list_before")"
ensure_http_status "product list before status change status" "$status_list_before" "200"
ensure_json_success "product list before status change success" "$body_list_before" || exit 1
found_before="$(echo "$body_list_before" | jq -r --arg id "$created_product_id" '((.data.items // .data.content // []) | map(.id|tostring) | index($id)) != null')"
if [[ "$found_before" == "false" ]]; then
  pass "DRAFT item not exposed in public PRODUCT list"
else
  fail "DRAFT item not exposed in public PRODUCT list (itemId=${created_product_id})"
fi

build_trusted_headers "$SELLER_ID" "SELLER,USER" "$SELLER_SID"
raw_status_change="$(curl -sS -w '\n%{http_code}' -X PATCH "http://127.0.0.1:${GW_PORT}/api/items/${created_product_id}/status" \
  -H "Content-Type: application/json" \
  "${AUTH_HEADERS[@]}" \
  -d '{"status":"ON_SALE","reason":"session e2e visibility"}')"
body_status_change="$(extract_http_body "$raw_status_change")"
status_status_change="$(extract_http_status "$raw_status_change")"
ensure_http_status "status change DRAFT->ON_SALE status" "$status_status_change" "200"
ensure_json_success "status change DRAFT->ON_SALE success" "$body_status_change" || exit 1

raw_list_after="$(curl -sS -w '\n%{http_code}' "http://127.0.0.1:${GW_PORT}/bff/v1/items?type=PRODUCT&size=100")"
body_list_after="$(extract_http_body "$raw_list_after")"
status_list_after="$(extract_http_status "$raw_list_after")"
ensure_http_status "product list after status change status" "$status_list_after" "200"
ensure_json_success "product list after status change success" "$body_list_after" || exit 1
found_after="$(echo "$body_list_after" | jq -r --arg id "$created_product_id" '((.data.items // .data.content // []) | map(.id|tostring) | index($id)) != null')"
if [[ "$found_after" == "true" ]]; then
  pass "ON_SALE item exposed in PRODUCT list"
else
  fail "ON_SALE item exposed in PRODUCT list (itemId=${created_product_id})"
fi

goods_payload="$(cat <<JSON
{"title":"Session E2E Goods ${ts}","description":"session auth e2e goods","price":21000,"storeId":${STORE_ID},"options":[{"optionName":"M","additionalPrice":0,"stockQuantity":5}],"shippingInfo":{"shippingFee":2500,"freeShippingThreshold":30000,"estimatedDays":2,"returnPolicy":"교환/환불 가능"},"linkedPerformanceItemIds":[]}
JSON
)"

build_trusted_headers "$SELLER_ID" "SELLER,USER" "$SELLER_SID"
raw_create_goods="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${GW_PORT}/bff/v1/goods" \
  -H "Content-Type: application/json" \
  "${AUTH_HEADERS[@]}" \
  -d "{\"item\":${goods_payload},\"images\":[]}")"
body_create_goods="$(extract_http_body "$raw_create_goods")"
status_create_goods="$(extract_http_status "$raw_create_goods")"
ensure_http_status "session-auth goods create status" "$status_create_goods" "200"
ensure_json_success "session-auth goods create success" "$body_create_goods" || exit 1
created_goods_id="$(echo "$body_create_goods" | jq -r '.data.id // empty')"
if [[ -z "$created_goods_id" || "$created_goods_id" == "null" ]]; then
  fail "created goods id resolved"
  exit 1
else
  pass "created goods id resolved (${created_goods_id})"
fi

build_trusted_headers "$SELLER_ID" "SELLER,USER" "$SELLER_SID"
raw_goods_status="$(curl -sS -w '\n%{http_code}' -X PATCH "http://127.0.0.1:${GW_PORT}/api/items/${created_goods_id}/status" \
  -H "Content-Type: application/json" \
  "${AUTH_HEADERS[@]}" \
  -d '{"status":"ON_SALE","reason":"session e2e type filter"}')"
body_goods_status="$(extract_http_body "$raw_goods_status")"
status_goods_status="$(extract_http_status "$raw_goods_status")"
ensure_http_status "goods status change DRAFT->ON_SALE status" "$status_goods_status" "200"
ensure_json_success "goods status change DRAFT->ON_SALE success" "$body_goods_status" || exit 1

raw_list_product_type="$(curl -sS -w '\n%{http_code}' "http://127.0.0.1:${GW_PORT}/bff/v1/items?type=PRODUCT&size=100")"
body_list_product_type="$(extract_http_body "$raw_list_product_type")"
status_list_product_type="$(extract_http_status "$raw_list_product_type")"
ensure_http_status "PRODUCT type list status" "$status_list_product_type" "200"
ensure_json_success "PRODUCT type list success" "$body_list_product_type" || exit 1
goods_in_product_list="$(echo "$body_list_product_type" | jq -r --arg id "$created_goods_id" '((.data.items // .data.content // []) | map(.id|tostring) | index($id)) != null')"
if [[ "$goods_in_product_list" == "false" ]]; then
  pass "type filter PRODUCT excludes GOODS item"
else
  fail "type filter PRODUCT excludes GOODS item (goodsItemId=${created_goods_id})"
fi

raw_list_goods_type="$(curl -sS -w '\n%{http_code}' "http://127.0.0.1:${GW_PORT}/bff/v1/items?type=GOODS&size=100")"
body_list_goods_type="$(extract_http_body "$raw_list_goods_type")"
status_list_goods_type="$(extract_http_status "$raw_list_goods_type")"
ensure_http_status "GOODS type list status" "$status_list_goods_type" "200"
ensure_json_success "GOODS type list success" "$body_list_goods_type" || exit 1
goods_in_goods_list="$(echo "$body_list_goods_type" | jq -r --arg id "$created_goods_id" '((.data.items // .data.content // []) | map(.id|tostring) | index($id)) != null')"
if [[ "$goods_in_goods_list" == "true" ]]; then
  pass "type filter GOODS includes GOODS item"
else
  fail "type filter GOODS includes GOODS item (goodsItemId=${created_goods_id})"
fi

build_trusted_headers "$BUYER_ID" "BUYER,USER" "$BUYER_SID"
raw_create_room="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${GW_PORT}/api/v1/chat/rooms/inquiries" \
  -H "Content-Type: application/json" \
  "${AUTH_HEADERS[@]}" \
  -d "{\"itemId\":${created_product_id}}")"
body_create_room="$(extract_http_body "$raw_create_room")"
status_create_room="$(extract_http_status "$raw_create_room")"
ensure_http_status "chat inquiry room create status" "$status_create_room" "200"
ensure_json_success "chat inquiry room create success" "$body_create_room" || exit 1

created_room_id="$(echo "$body_create_room" | jq -r '.data.roomId // empty')"
created_room_item_id="$(echo "$body_create_room" | jq -r '.data.itemId // empty')"
participants_csv="$(echo "$body_create_room" | jq -r '.data.participants // [] | map(.userId|tostring) | sort | join(",")')"
expected_participants_csv="$(printf '%s\n%s\n' "$BUYER_ID" "$SELLER_ID" | sort -n | paste -sd, -)"

if [[ -n "$created_room_id" && "$created_room_id" != "null" ]]; then
  pass "chat room id resolved (${created_room_id})"
else
  fail "chat room id resolved"
fi
if [[ "$created_room_item_id" == "$created_product_id" ]]; then
  pass "chat room bound to created product item"
else
  fail "chat room bound to created product item (expected=${created_product_id}, actual=${created_room_item_id})"
fi
if [[ "$participants_csv" == "$expected_participants_csv" ]]; then
  pass "chat domain uses forwarded buyer/seller user id headers"
else
  fail "chat domain uses forwarded buyer/seller user id headers (expected=${expected_participants_csv}, actual=${participants_csv})"
fi

build_trusted_headers "$BUYER_ID" "BUYER,USER" "$BUYER_SID"
raw_room_list="$(curl -sS -w '\n%{http_code}' "http://127.0.0.1:${GW_PORT}/api/v1/chat/rooms?size=20" \
  "${AUTH_HEADERS[@]}")"
body_room_list="$(extract_http_body "$raw_room_list")"
status_room_list="$(extract_http_status "$raw_room_list")"
ensure_http_status "chat room list by buyer status" "$status_room_list" "200"
ensure_json_success "chat room list by buyer success" "$body_room_list" || exit 1
room_list_contains="$(echo "$body_room_list" | jq -r --arg id "$created_room_id" '((.data.items // .data.content // []) | map(.roomId|tostring) | index($id)) != null')"
if [[ "$room_list_contains" == "true" ]]; then
  pass "chat room list includes newly created room for buyer"
else
  fail "chat room list includes newly created room for buyer (roomId=${created_room_id})"
fi

echo "[INFO] result passes=${PASSES} failures=${FAILURES} warnings=${WARNINGS}"
if [[ "$FAILURES" -gt 0 ]]; then
  exit 1
fi

pass "gateway session userid + item search e2e passed"
