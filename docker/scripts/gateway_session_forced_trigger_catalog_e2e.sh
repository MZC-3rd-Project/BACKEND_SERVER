#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOG_DIR="${LOG_DIR:-/tmp/gateway-session-forced-trigger-catalog-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$LOG_DIR"

GW_PORT="${GW_PORT:-18673}"
PRODUCT_PORT="${PRODUCT_PORT:-18684}"
FUNDING_PORT="${FUNDING_PORT:-18686}"
SEARCH_PORT="${SEARCH_PORT:-18688}"
HOTDEAL_PORT="${HOTDEAL_PORT:-18689}"
ELASTICSEARCH_PORT="${ELASTICSEARCH_PORT:-23173}"

REDIS_CONTAINER="${REDIS_CONTAINER:-project03-redis}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-project03-kafka}"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/.gradle-codex-e2e}"

SELLER_ID="${SELLER_ID:-910001}"
STORE_ID="${STORE_ID:-930001}"
SELLER_SID="${SELLER_SID:-sid-seller-910001}"

INTERNAL_AUTH_TOKEN="${INTERNAL_AUTH_TOKEN:-gw-session-forced-trigger-token}"

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
  for _ in {1..180}; do
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

wait_es_up() {
  for _ in {1..120}; do
    local code
    code="$(curl -sS -o /dev/null -w "%{http_code}" --max-time 2 "http://127.0.0.1:${ELASTICSEARCH_PORT}/_cluster/health" || true)"
    if [[ "$code" == "200" ]]; then
      pass "elasticsearch is reachable"
      return 0
    fi
    sleep 1
  done
  fail "elasticsearch reachability timeout"
  return 1
}

wait_gateway_ready() {
  for _ in {1..180}; do
    local code
    code="$(curl -sS -o /dev/null -w "%{http_code}" --max-time 2 --get \
      "http://127.0.0.1:${GW_PORT}/bff/v1/catalog/items" \
      --data-urlencode "q=ready-check" \
      --data "size=1" || true)"
    if [[ "$code" == "200" ]]; then
      pass "client-gateway ready for catalog BFF"
      return 0
    fi
    sleep 1
  done
  fail "client-gateway ready timeout"
  return 1
}

evict_search_cache_keys() {
  docker exec -i "$REDIS_CONTAINER" sh -lc \
    "redis-cli --scan --pattern 'search:cache:*' | xargs -r redis-cli DEL >/dev/null" >/dev/null 2>&1 || true
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
  local body="${4:-}"
  if [[ "$actual" == "$expected" ]]; then
    pass "$label"
    return 0
  fi
  fail "$label (expected=${expected}, actual=${actual})"
  if [[ -n "$body" ]]; then
    echo "[DEBUG] ${label} response=${body}"
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

matches_expected_id() {
  local actual="$1"
  local expected="$2"

  case "$expected" in
    NONE)
      [[ -z "$actual" ]]
      ;;
    ANY)
      [[ -n "$actual" ]]
      ;;
    *)
      [[ "$actual" == "$expected" ]]
      ;;
  esac
}

wait_catalog_state() {
  local label="$1"
  local item_id="$2"
  local query="$3"
  local expected_channel="$4"
  local expected_status="$5"
  local expected_campaign="$6"
  local expected_hotdeal="$7"
  local timeout_seconds="${8:-120}"

  local last_item_json=""
  for _ in $(seq 1 "$timeout_seconds"); do
    local raw body code success item_json channel status campaign_id hotdeal_id

    evict_search_cache_keys

    raw="$(curl -sS -w '\n%{http_code}' --max-time 5 --get \
      "http://127.0.0.1:${GW_PORT}/bff/v1/catalog/items" \
      --data-urlencode "q=${query}" \
      --data "size=20" \
      --data "channel=ALL" 2>/dev/null || true)"

    body="$(extract_http_body "$raw")"
    code="$(extract_http_status "$raw")"

    if [[ "$code" != "200" ]]; then
      sleep 1
      continue
    fi

    success="$(echo "$body" | jq -r '.success // false' 2>/dev/null || true)"
    if [[ "$success" != "true" ]]; then
      sleep 1
      continue
    fi

    item_json="$(echo "$body" | jq -c --arg id "$item_id" '.data.items[]? | select((.itemId | tostring) == $id)' | head -n1)"
    if [[ -z "$item_json" ]]; then
      sleep 1
      continue
    fi

    channel="$(echo "$item_json" | jq -r '.salesChannel // ""')"
    status="$(echo "$item_json" | jq -r '.status // ""')"
    campaign_id="$(echo "$item_json" | jq -r '.activeCampaignId // empty')"
    hotdeal_id="$(echo "$item_json" | jq -r '.activeHotDealId // empty')"

    last_item_json="$item_json"

    if [[ "$channel" == "$expected_channel" && "$status" == "$expected_status" ]] \
      && matches_expected_id "$campaign_id" "$expected_campaign" \
      && matches_expected_id "$hotdeal_id" "$expected_hotdeal"; then
      local safe_label
      safe_label="$(printf '%s' "$label" | tr -c 'A-Za-z0-9._-' '_')"
      pass "$label"
      printf '%s\n' "$item_json" >"$LOG_DIR/${safe_label}.json"
      return 0
    fi

    sleep 1
  done

  fail "$label (expected channel=${expected_channel}, status=${expected_status}, campaign=${expected_campaign}, hotdeal=${expected_hotdeal}, last=${last_item_json})"
  return 1
}

wait_item_history_contains_status() {
  local item_id="$1"
  local expected_status="$2"
  local timeout_seconds="${3:-90}"

  for _ in $(seq 1 "$timeout_seconds"); do
    local raw body code success found
    raw="$(curl -sS -w '\n%{http_code}' --max-time 5 \
      "http://127.0.0.1:${GW_PORT}/api/items/${item_id}/status-history" 2>/dev/null || true)"
    body="$(extract_http_body "$raw")"
    code="$(extract_http_status "$raw")"

    if [[ "$code" != "200" ]]; then
      sleep 1
      continue
    fi

    success="$(echo "$body" | jq -r '.success // false' 2>/dev/null || true)"
    if [[ "$success" != "true" ]]; then
      sleep 1
      continue
    fi

    found="$(echo "$body" | jq -r --arg status "$expected_status" '[.data[]? | select(.newStatus == $status)] | length > 0')"
    if [[ "$found" == "true" ]]; then
      pass "item status history contains ${expected_status}"
      return 0
    fi

    sleep 1
  done

  fail "item status history missing ${expected_status}"
  return 1
}

wait_catalog_channel_contains_item() {
  local label="$1"
  local query="$2"
  local channel="$3"
  local item_id="$4"
  local expected_contains="$5"
  local timeout_seconds="${6:-60}"

  for _ in $(seq 1 "$timeout_seconds"); do
    local raw body code success contains

    evict_search_cache_keys

    raw="$(curl -sS -w '\n%{http_code}' --max-time 5 --get \
      "http://127.0.0.1:${GW_PORT}/bff/v1/catalog/items" \
      --data-urlencode "q=${query}" \
      --data "size=20" \
      --data "channel=${channel}" 2>/dev/null || true)"
    body="$(extract_http_body "$raw")"
    code="$(extract_http_status "$raw")"

    if [[ "$code" != "200" ]]; then
      sleep 1
      continue
    fi

    success="$(echo "$body" | jq -r '.success // false' 2>/dev/null || true)"
    if [[ "$success" != "true" ]]; then
      sleep 1
      continue
    fi

    contains="$(echo "$body" | jq -r --arg id "$item_id" '((.data.items // []) | map(.itemId|tostring) | index($id)) != null')"
    if [[ "$contains" == "$expected_contains" ]]; then
      pass "$label"
      return 0
    fi

    sleep 1
  done

  fail "$label"
  return 1
}

recreate_search_index() {
  local attempt max_attempts raw body status success code message
  max_attempts=10

  for attempt in $(seq 1 "$max_attempts"); do
    raw="$(curl -sS -w '\n%{http_code}' -X POST \
      "http://127.0.0.1:${SEARCH_PORT}/internal/v1/search/indexes/recreate?indexName=items")"
    body="$(extract_http_body "$raw")"
    status="$(extract_http_status "$raw")"
    success="$(echo "$body" | jq -r '.success // false' 2>/dev/null || true)"

    if [[ "$status" == "200" && "$success" == "true" ]]; then
      pass "search index recreate success"
      if [[ "$attempt" -gt 1 ]]; then
        warn "search index recreate succeeded after retry (attempt=${attempt})"
      fi
      return 0
    fi

    if [[ "$attempt" -lt "$max_attempts" ]]; then
      sleep 2
    fi
  done

  code="$(echo "$body" | jq -r '.error.code // "UNKNOWN"' 2>/dev/null || true)"
  message="$(echo "$body" | jq -r '.error.message // ""' 2>/dev/null || true)"
  fail "search index recreate failed (status=${status}, code=${code}, message=${message})"
  return 1
}

reset_consumer_offsets() {
  local group="$1"
  local topic="$2"
  docker exec -i "$KAFKA_CONTAINER" kafka-consumer-groups \
    --bootstrap-server localhost:9092 \
    --group "$group" \
    --topic "$topic" \
    --reset-offsets --to-latest --execute >/dev/null 2>&1 || true
}

produce_forced_funding_succeeded_event() {
  local campaign_id="$1"
  local item_id="$2"
  local seller_id="$3"
  local event_id json_payload quoted_payload line

  event_id="evt-force-funding-succeeded-${item_id}-$(date +%s)-$RANDOM"
  json_payload="$(cat <<JSON
{"eventId":"${event_id}","eventType":"FUNDING_SUCCEEDED","campaignId":${campaign_id},"itemId":${item_id},"sellerId":${seller_id},"fundingType":"AMOUNT_BASED","goalAmount":1000,"currentAmount":1000,"currentQuantity":1}
JSON
)"
  quoted_payload="$(printf '%s' "$json_payload" | jq -Rs .)"
  line="__TypeId__:java.lang.String"$'\t'"${quoted_payload}"

  docker exec -i "$KAFKA_CONTAINER" kafka-console-producer \
    --bootstrap-server localhost:9092 \
    --topic funding-events \
    --property parse.headers=true >/dev/null <<<"${line}"

  pass "forced funding succeeded event published (${event_id})"
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
check_port_free "$FUNDING_PORT"
check_port_free "$SEARCH_PORT"
check_port_free "$HOTDEAL_PORT"


echo "[INFO] preparing docker infra"
(cd "$ROOT/docker" && docker compose up -d postgres redis zookeeper kafka elasticsearch >/dev/null)
wait_es_up

echo "[INFO] resetting kafka consumer offsets for deterministic run"
reset_consumer_offsets "search-service-group" "item-events"
reset_consumer_offsets "search-service-group" "funding-events"
reset_consumer_offsets "search-service-group" "hotdeal-events"
reset_consumer_offsets "product-service-group" "funding-events"
pass "consumer offsets reset (best effort)"

echo "[INFO] seeding redis session sid=${SELLER_SID}"
docker exec -i "$REDIS_CONTAINER" redis-cli HSET "gateway:sess:${SELLER_SID}" status ACTIVE >/dev/null
pass "redis seeded active sid"

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

echo "[INFO] starting funding-service on ${FUNDING_PORT}"
(
  cd "$ROOT"
  env \
    GRADLE_USER_HOME="$GRADLE_USER_HOME" \
    SERVER_PORT="$FUNDING_PORT" \
    APP_SECURITY_CONTEXT_SIGNING_KEY="$SIGNING_KEY" \
    APP_SECURITY_CONTEXT_MAX_AGE_MILLIS="$MAX_AGE_MILLIS" \
    APP_GATEWAY_SECURITY_ENABLED=true \
    APP_GATEWAY_SECURITY_INTERNAL_AUTH_TOKEN="$INTERNAL_AUTH_TOKEN" \
    ./gradlew :servers:services:funding:bootRun --no-daemon >"$LOG_DIR/funding.log" 2>&1
) &
PIDS+=("$!")

echo "[INFO] starting hot-deal-service on ${HOTDEAL_PORT}"
(
  cd "$ROOT"
  env \
    GRADLE_USER_HOME="$GRADLE_USER_HOME" \
    SERVER_PORT="$HOTDEAL_PORT" \
    PRODUCT_SERVICE_URL="http://127.0.0.1:${PRODUCT_PORT}" \
    APP_SECURITY_CONTEXT_SIGNING_KEY="$SIGNING_KEY" \
    APP_SECURITY_CONTEXT_MAX_AGE_MILLIS="$MAX_AGE_MILLIS" \
    APP_GATEWAY_SECURITY_ENABLED=true \
    APP_GATEWAY_SECURITY_INTERNAL_AUTH_TOKEN="$INTERNAL_AUTH_TOKEN" \
    ./gradlew :servers:services:hot-deal:bootRun --no-daemon >"$LOG_DIR/hotdeal.log" 2>&1
) &
PIDS+=("$!")

echo "[INFO] starting search-service on ${SEARCH_PORT}"
(
  cd "$ROOT"
  env \
    GRADLE_USER_HOME="$GRADLE_USER_HOME" \
    SERVER_PORT="$SEARCH_PORT" \
    ELASTICSEARCH_URIS="http://127.0.0.1:${ELASTICSEARCH_PORT}" \
    SEARCH_THUMBNAIL_ENRICHER_ENABLED=false \
    APP_GATEWAY_SECURITY_ENABLED=false \
    ./gradlew :servers:services:search:bootRun --no-daemon >"$LOG_DIR/search.log" 2>&1
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
    APP_SERVICE_FUNDING_URL="http://127.0.0.1:${FUNDING_PORT}" \
    APP_SERVICE_HOT_DEAL_URL="http://127.0.0.1:${HOTDEAL_PORT}" \
    APP_SERVICE_SEARCH_URL="http://127.0.0.1:${SEARCH_PORT}" \
    ./gradlew :servers:gateways:client-gateway:bootRun --no-daemon >"$LOG_DIR/gateway.log" 2>&1
) &
PIDS+=("$!")

wait_health "product-service" "$PRODUCT_PORT"
wait_health "funding-service" "$FUNDING_PORT"
wait_health "hot-deal-service" "$HOTDEAL_PORT"
wait_health "search-service" "$SEARCH_PORT"
recreate_search_index
wait_gateway_ready

ts="$(date +%s)"
keyword="forced-flow-${ts}"
product_payload="$(cat <<JSON
{"title":"Forced Flow Product ${keyword}","description":"forced trigger e2e","price":14500,"storeId":${STORE_ID},"options":[{"optionName":"default","additionalPrice":0,"stockQuantity":10}],"shippingInfo":{"shippingFee":3000,"freeShippingThreshold":40000,"estimatedDays":2,"returnPolicy":"returnable in 7 days"}}
JSON
)"

build_trusted_headers "$SELLER_ID" "SELLER,USER" "$SELLER_SID"
raw_create_product="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${GW_PORT}/bff/v1/products" \
  -H "Content-Type: application/json" \
  "${AUTH_HEADERS[@]}" \
  -d "{\"item\":${product_payload},\"images\":[]}")"
body_create_product="$(extract_http_body "$raw_create_product")"
status_create_product="$(extract_http_status "$raw_create_product")"
ensure_http_status "create product status" "$status_create_product" "200" "$body_create_product" || exit 1
ensure_json_success "create product success" "$body_create_product" || exit 1

created_product_id="$(echo "$body_create_product" | jq -r '.data.id // empty')"
created_seller_id="$(echo "$body_create_product" | jq -r '.data.sellerId // empty')"
if [[ -z "$created_product_id" || "$created_product_id" == "null" ]]; then
  fail "created product id resolved"
  exit 1
fi
pass "created product id resolved (${created_product_id})"

if [[ "$created_seller_id" == "$SELLER_ID" ]]; then
  pass "forwarded X-User-Id used for product create"
else
  fail "forwarded X-User-Id used for product create (expected=${SELLER_ID}, actual=${created_seller_id})"
  exit 1
fi

build_trusted_headers "$SELLER_ID" "SELLER,USER" "$SELLER_SID"
raw_to_funding="$(curl -sS -w '\n%{http_code}' -X PATCH "http://127.0.0.1:${GW_PORT}/api/items/${created_product_id}/status" \
  -H "Content-Type: application/json" \
  "${AUTH_HEADERS[@]}" \
  -d '{"status":"FUNDING","reason":"forced trigger phase funding"}')"
body_to_funding="$(extract_http_body "$raw_to_funding")"
status_to_funding="$(extract_http_status "$raw_to_funding")"
ensure_http_status "status change DRAFT->FUNDING" "$status_to_funding" "200" "$body_to_funding" || exit 1
ensure_json_success "status change DRAFT->FUNDING success" "$body_to_funding" || exit 1

now_epoch="$(date +%s)"
start_at="$(date -r $((now_epoch - 60)) '+%Y-%m-%dT%H:%M:%S' 2>/dev/null || date -d "@$((now_epoch - 60))" '+%Y-%m-%dT%H:%M:%S')"
end_at="$(date -r $((now_epoch + 3600)) '+%Y-%m-%dT%H:%M:%S' 2>/dev/null || date -d "@$((now_epoch + 3600))" '+%Y-%m-%dT%H:%M:%S')"

campaign_payload="$(cat <<JSON
{"itemId":${created_product_id},"fundingType":"AMOUNT_BASED","goalAmount":1000,"goalQuantity":null,"minAmount":1000,"startAt":"${start_at}","endAt":"${end_at}"}
JSON
)"

build_trusted_headers "$SELLER_ID" "SELLER,USER" "$SELLER_SID"
raw_create_campaign="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${GW_PORT}/api/campaigns" \
  -H "Content-Type: application/json" \
  "${AUTH_HEADERS[@]}" \
  -d "$campaign_payload")"
body_create_campaign="$(extract_http_body "$raw_create_campaign")"
status_create_campaign="$(extract_http_status "$raw_create_campaign")"
ensure_http_status "create campaign status" "$status_create_campaign" "200" "$body_create_campaign" || exit 1
ensure_json_success "create campaign success" "$body_create_campaign" || exit 1

campaign_id="$(echo "$body_create_campaign" | jq -r '.data.id // empty')"
campaign_seller_id="$(echo "$body_create_campaign" | jq -r '.data.sellerId // empty')"
if [[ -z "$campaign_id" || "$campaign_id" == "null" ]]; then
  fail "created campaign id resolved"
  exit 1
fi
pass "created campaign id resolved (${campaign_id})"
if [[ "$campaign_seller_id" == "$SELLER_ID" ]]; then
  pass "forwarded X-User-Id used for campaign create"
else
  fail "forwarded X-User-Id used for campaign create (expected=${SELLER_ID}, actual=${campaign_seller_id})"
  exit 1
fi

wait_catalog_state \
  "catalog state is FUNDING after campaign create" \
  "$created_product_id" \
  "$keyword" \
  "FUNDING" \
  "FUNDING" \
  "$campaign_id" \
  "NONE" \
  120 || exit 1

produce_forced_funding_succeeded_event "$campaign_id" "$created_product_id" "$SELLER_ID"

wait_item_history_contains_status "$created_product_id" "FUNDED" 120 || exit 1

wait_catalog_state \
  "catalog state closes campaign and keeps FUNDED channel before sales reopen" \
  "$created_product_id" \
  "$keyword" \
  "FUNDING" \
  "FUNDED" \
  "NONE" \
  "NONE" \
  120 || exit 1

build_trusted_headers "$SELLER_ID" "SELLER,USER" "$SELLER_SID"
raw_to_on_sale="$(curl -sS -w '\n%{http_code}' -X PATCH "http://127.0.0.1:${GW_PORT}/api/items/${created_product_id}/status" \
  -H "Content-Type: application/json" \
  "${AUTH_HEADERS[@]}" \
  -d '{"status":"ON_SALE","reason":"forced trigger phase sales"}')"
body_to_on_sale="$(extract_http_body "$raw_to_on_sale")"
status_to_on_sale="$(extract_http_status "$raw_to_on_sale")"
ensure_http_status "status change FUNDED->ON_SALE" "$status_to_on_sale" "200" "$body_to_on_sale" || exit 1
ensure_json_success "status change FUNDED->ON_SALE success" "$body_to_on_sale" || exit 1

wait_catalog_state \
  "catalog state is NORMAL/ON_SALE" \
  "$created_product_id" \
  "$keyword" \
  "NORMAL" \
  "ON_SALE" \
  "NONE" \
  "NONE" \
  120 || exit 1

hotdeal_payload="$(cat <<JSON
{"itemId":${created_product_id},"discountRate":15,"maxQuantity":30,"maxPerUser":2}
JSON
)"

build_trusted_headers "$SELLER_ID" "SELLER,USER" "$SELLER_SID"
raw_create_hotdeal="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${GW_PORT}/api/v1/hot-deals" \
  -H "Content-Type: application/json" \
  "${AUTH_HEADERS[@]}" \
  -d "$hotdeal_payload")"
body_create_hotdeal="$(extract_http_body "$raw_create_hotdeal")"
status_create_hotdeal="$(extract_http_status "$raw_create_hotdeal")"
ensure_http_status "create hotdeal status" "$status_create_hotdeal" "200" "$body_create_hotdeal" || exit 1
ensure_json_success "create hotdeal success" "$body_create_hotdeal" || exit 1

hotdeal_id="$(echo "$body_create_hotdeal" | jq -r '.data.id // empty')"
if [[ -z "$hotdeal_id" || "$hotdeal_id" == "null" ]]; then
  fail "created hotdeal id resolved"
  exit 1
fi
pass "created hotdeal id resolved (${hotdeal_id})"

wait_catalog_state \
  "catalog state is HOT_DEAL" \
  "$created_product_id" \
  "$keyword" \
  "HOT_DEAL" \
  "HOT_DEAL" \
  "NONE" \
  "$hotdeal_id" \
  120 || exit 1

wait_catalog_channel_contains_item \
  "channel HOT_DEAL contains target item" \
  "$keyword" \
  "HOT_DEAL" \
  "$created_product_id" \
  "true" \
  60 || exit 1

wait_catalog_channel_contains_item \
  "channel NORMAL excludes hotdeal item" \
  "$keyword" \
  "NORMAL" \
  "$created_product_id" \
  "false" \
  60 || exit 1


echo "[INFO] summary: passes=${PASSES} failures=${FAILURES} warnings=${WARNINGS}"
if [[ "$FAILURES" -gt 0 ]]; then
  exit 1
fi

pass "gateway session forced trigger catalog e2e passed"
