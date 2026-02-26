#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOG_DIR="${LOG_DIR:-/tmp/full_stack_publish_chain_e2e_logs_$(date +%Y%m%d_%H%M%S)}"
mkdir -p "$LOG_DIR"

GW_PORT="${GW_PORT:-18181}"
PRODUCT_PORT="${PRODUCT_PORT:-18084}"
STOCK_PORT="${STOCK_PORT:-18085}"
FUNDING_PORT="${FUNDING_PORT:-18086}"
SALES_PORT="${SALES_PORT:-18087}"
SEARCH_PORT="${SEARCH_PORT:-18088}"
HOTDEAL_PORT="${HOTDEAL_PORT:-18089}"
ANALYTICS_PORT="${ANALYTICS_PORT:-18095}"
ELASTICSEARCH_PORT="${ELASTICSEARCH_PORT:-23173}"

POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-project03-postgres}"
REDIS_CONTAINER="${REDIS_CONTAINER:-project03-redis}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-project03-kafka}"

INTERNAL_AUTH_TOKEN="${INTERNAL_AUTH_TOKEN:-full-chain-e2e-token}"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/.gradle-codex-e2e}"

NOW_TS="$(date +%s)"
STORE_ID="${STORE_ID:-$((990000 + NOW_TS % 100000))}"
SELLER_ID="${SELLER_ID:-$((880000 + NOW_TS % 100000))}"
BUYER_ID="${BUYER_ID:-$((770000 + NOW_TS % 100000))}"
TARGET_DATE="${TARGET_DATE:-$(date +%Y-%m-%d)}"
SEARCH_SESSION_ID="${SEARCH_SESSION_ID:-full-chain-sess-${NOW_TS}}"
CONSUMER_GROUP_SUFFIX="${CONSUMER_GROUP_SUFFIX:-$NOW_TS}"
STOCK_CONSUMER_GROUP="${STOCK_CONSUMER_GROUP:-stock-service-group-e2e-${CONSUMER_GROUP_SUFFIX}}"
PRODUCT_CONSUMER_GROUP="${PRODUCT_CONSUMER_GROUP:-product-service-group-e2e-${CONSUMER_GROUP_SUFFIX}}"
SALES_CONSUMER_GROUP="${SALES_CONSUMER_GROUP:-sales-service-group-e2e-${CONSUMER_GROUP_SUFFIX}}"
FUNDING_CONSUMER_GROUP="${FUNDING_CONSUMER_GROUP:-funding-service-group-e2e-${CONSUMER_GROUP_SUFFIX}}"
HOTDEAL_CONSUMER_GROUP="${HOTDEAL_CONSUMER_GROUP:-hot-deal-service-group-e2e-${CONSUMER_GROUP_SUFFIX}}"
SEARCH_CONSUMER_GROUP="${SEARCH_CONSUMER_GROUP:-search-service-group-e2e-${CONSUMER_GROUP_SUFFIX}}"
ANALYTICS_CONSUMER_GROUP="${ANALYTICS_CONSUMER_GROUP:-analytics-dashboard-service-group-e2e-${CONSUMER_GROUP_SUFFIX}}"

PIDS=()
PASSES=0
FAILURES=0
WARNINGS=0

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
    fail "port $port is already in use"
    return 1
  fi
  pass "port $port is free"
}

wait_health() {
  local name="$1"
  local port="$2"
  for _ in {1..240}; do
    local status
    status="$(curl -sS --max-time 2 "http://127.0.0.1:${port}/actuator/health" | jq -r '.status' 2>/dev/null || true)"
    if [[ "$status" == "UP" ]]; then
      pass "$name health is UP"
      return 0
    fi
    sleep 1
  done

  fail "$name health timeout"
  return 1
}

wait_es_up() {
  for _ in {1..120}; do
    local status
    status="$(curl -sS "http://127.0.0.1:${ELASTICSEARCH_PORT}/_cluster/health" | jq -r '.status' 2>/dev/null || true)"
    if [[ "$status" == "green" || "$status" == "yellow" ]]; then
      pass "elasticsearch health is ${status}"
      return 0
    fi
    sleep 1
  done

  fail "elasticsearch health timeout"
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
  else
    local code
    code="$(echo "$body" | jq -r '.error.code // "UNKNOWN"')"
    fail "$label (errorCode=${code})"
  fi
}

ensure_jq_true() {
  local label="$1"
  local body="$2"
  local expr="$3"
  if echo "$body" | jq -e "$expr" >/dev/null; then
    pass "$label"
  else
    fail "$label (expr=${expr})"
  fi
}

psql_exec() {
  local db="$1"
  local sql="$2"
  docker exec -i "$POSTGRES_CONTAINER" psql -U postgres -d "$db" -v ON_ERROR_STOP=1 -c "$sql" >/dev/null
}

psql_query() {
  local db="$1"
  local sql="$2"
  docker exec -i "$POSTGRES_CONTAINER" psql -U postgres -d "$db" -At -c "$sql"
}

ensure_analytics_db() {
  local exists
  exists="$(docker exec -i "$POSTGRES_CONTAINER" psql -U postgres -d postgres -At -c "SELECT 1 FROM pg_database WHERE datname='analytics_db'" 2>/dev/null || true)"
  if [[ "$exists" != "1" ]]; then
    docker exec -i "$POSTGRES_CONTAINER" psql -U postgres -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE analytics_db" >/dev/null
    pass "analytics_db created"
  else
    pass "analytics_db already exists"
  fi
}

topic_offset_sum() {
  local topic="$1"
  local offsets
  offsets="$(docker exec -i "$KAFKA_CONTAINER" bash -lc "kafka-run-class kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic ${topic} --time -1 2>/dev/null" || true)"
  echo "$offsets" | awk -F: '{sum+=$3} END {print sum+0}'
}

wait_stock_item_id() {
  local item_id="$1"
  for _ in {1..120}; do
    local raw body status stock_item_id
    raw="$(curl -sS -w '\n%{http_code}' \
      "http://127.0.0.1:${STOCK_PORT}/internal/v1/stock/items/${item_id}" \
      -H "X-Gateway-Auth: ${INTERNAL_AUTH_TOKEN}" \
      -H "X-User-Id: ${SELLER_ID}" || true)"
    body="$(extract_http_body "$raw")"
    status="$(extract_http_status "$raw")"
    if [[ "$status" == "200" ]] && [[ "$(echo "$body" | jq -r '.success // false')" == "true" ]]; then
      stock_item_id="$(echo "$body" | jq -r '.data.stocks[0].stockItemId // empty')"
      if [[ -n "$stock_item_id" && "$stock_item_id" != "null" ]]; then
        echo "$stock_item_id"
        return 0
      fi
    fi
    sleep 2
  done
  return 1
}

wait_count_ge() {
  local db="$1"
  local sql="$2"
  local threshold="$3"
  local label="$4"

  for _ in {1..120}; do
    local value
    value="$(psql_query "$db" "$sql" 2>/dev/null | tr -d '[:space:]' || true)"
    if [[ "$value" =~ ^[0-9]+$ ]] && (( value >= threshold )); then
      pass "$label (value=${value})"
      return 0
    fi
    sleep 1
  done

  fail "$label (threshold=${threshold})"
  return 1
}

wait_count_eq() {
  local db="$1"
  local sql="$2"
  local expected="$3"
  local label="$4"

  for _ in {1..120}; do
    local value
    value="$(psql_query "$db" "$sql" 2>/dev/null | tr -d '[:space:]' || true)"
    if [[ "$value" =~ ^[0-9]+$ ]] && (( value == expected )); then
      pass "$label (value=${value})"
      return 0
    fi
    sleep 1
  done

  fail "$label (expected=${expected})"
  return 1
}

wait_consumer_assignment() {
  local group="$1"
  local topic="$2"
  local label="$3"

  for _ in {1..180}; do
    local desc
    desc="$(docker exec -i "$KAFKA_CONTAINER" bash -lc "kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group ${group} 2>/dev/null" || true)"

    if echo "$desc" | awk -v g="$group" -v t="$topic" '
      $1 == g && $2 == t {found=1}
      END {exit(found ? 0 : 1)}
    '; then
      pass "$label"
      return 0
    fi

    sleep 1
  done

  fail "$label timeout"
  return 1
}

start_services() {
  echo "[INFO] starting stock-service on ${STOCK_PORT}"
  (
    cd "$ROOT"
    env \
      GRADLE_USER_HOME="$GRADLE_USER_HOME" \
      SERVER_PORT="$STOCK_PORT" \
      SPRING_KAFKA_CONSUMER_GROUP_ID="$STOCK_CONSUMER_GROUP" \
      SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET=latest \
      APP_GATEWAY_SECURITY_ENABLED=true \
      APP_GATEWAY_SECURITY_INTERNAL_AUTH_TOKEN="$INTERNAL_AUTH_TOKEN" \
      ./gradlew :servers:services:stock:bootRun --no-daemon >"$LOG_DIR/stock.log" 2>&1
  ) &
  PIDS+=("$!")

  echo "[INFO] starting product-service on ${PRODUCT_PORT}"
  (
    cd "$ROOT"
    env \
      GRADLE_USER_HOME="$GRADLE_USER_HOME" \
      SERVER_PORT="$PRODUCT_PORT" \
      SPRING_KAFKA_CONSUMER_GROUP_ID="$PRODUCT_CONSUMER_GROUP" \
      SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET=latest \
      APP_GATEWAY_SECURITY_ENABLED=true \
      APP_GATEWAY_SECURITY_INTERNAL_AUTH_TOKEN="$INTERNAL_AUTH_TOKEN" \
      ./gradlew :servers:services:product:bootRun --no-daemon >"$LOG_DIR/product.log" 2>&1
  ) &
  PIDS+=("$!")

  echo "[INFO] starting sales-service on ${SALES_PORT}"
  (
    cd "$ROOT"
    env \
      GRADLE_USER_HOME="$GRADLE_USER_HOME" \
      SERVER_PORT="$SALES_PORT" \
      SPRING_KAFKA_CONSUMER_GROUP_ID="$SALES_CONSUMER_GROUP" \
      SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET=latest \
      PRODUCT_SERVICE_URL="http://127.0.0.1:${PRODUCT_PORT}" \
      STOCK_SERVICE_URL="http://127.0.0.1:${STOCK_PORT}" \
      APP_GATEWAY_SECURITY_ENABLED=true \
      APP_GATEWAY_SECURITY_INTERNAL_AUTH_TOKEN="$INTERNAL_AUTH_TOKEN" \
      ./gradlew :servers:services:sales:bootRun --no-daemon >"$LOG_DIR/sales.log" 2>&1
  ) &
  PIDS+=("$!")

  echo "[INFO] starting funding-service on ${FUNDING_PORT}"
  (
    cd "$ROOT"
    env \
      GRADLE_USER_HOME="$GRADLE_USER_HOME" \
      SERVER_PORT="$FUNDING_PORT" \
      SPRING_KAFKA_CONSUMER_GROUP_ID="$FUNDING_CONSUMER_GROUP" \
      SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET=latest \
      STOCK_SERVICE_URL="http://127.0.0.1:${STOCK_PORT}" \
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
      SPRING_KAFKA_CONSUMER_GROUP_ID="$HOTDEAL_CONSUMER_GROUP" \
      SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET=latest \
      PRODUCT_SERVICE_URL="http://127.0.0.1:${PRODUCT_PORT}" \
      STOCK_SERVICE_URL="http://127.0.0.1:${STOCK_PORT}" \
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
      SPRING_KAFKA_CONSUMER_GROUP_ID="$SEARCH_CONSUMER_GROUP" \
      SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET=latest \
      ELASTICSEARCH_URIS="http://127.0.0.1:${ELASTICSEARCH_PORT}" \
      SEARCH_THUMBNAIL_ENRICHER_ENABLED=false \
      APP_GATEWAY_SECURITY_ENABLED=false \
      ./gradlew :servers:services:search:bootRun --no-daemon >"$LOG_DIR/search.log" 2>&1
  ) &
  PIDS+=("$!")

  echo "[INFO] starting analytics-dashboard on ${ANALYTICS_PORT}"
  (
    cd "$ROOT"
    env \
      GRADLE_USER_HOME="$GRADLE_USER_HOME" \
      SERVER_PORT="$ANALYTICS_PORT" \
      SPRING_KAFKA_CONSUMER_GROUP_ID="$ANALYTICS_CONSUMER_GROUP" \
      SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET=latest \
      DB_URL="jdbc:postgresql://127.0.0.1:5432/analytics_db" \
      DB_USERNAME="postgres" \
      DB_PASSWORD="postgres" \
      APP_GATEWAY_SECURITY_ENABLED=true \
      APP_GATEWAY_SECURITY_GATEWAY_AUTH_ENABLED=true \
      APP_GATEWAY_SECURITY_INTERNAL_AUTH_TOKEN="$INTERNAL_AUTH_TOKEN" \
      ./gradlew :servers:services:analytics-dashboard:bootRun --no-daemon >"$LOG_DIR/analytics.log" 2>&1
  ) &
  PIDS+=("$!")

  echo "[INFO] starting client-gateway on ${GW_PORT}"
  (
    cd "$ROOT"
    env \
      GRADLE_USER_HOME="$GRADLE_USER_HOME" \
      SERVER_PORT="$GW_PORT" \
      GATEWAY_AUTH_ENABLED=false \
      GATEWAY_SESSION_ENABLED=false \
      GATEWAY_SECURITY_INTERNAL_AUTH_TOKEN="$INTERNAL_AUTH_TOKEN" \
      APP_SERVICE_PRODUCT_URL="http://127.0.0.1:${PRODUCT_PORT}" \
      APP_SERVICE_STOCK_URL="http://127.0.0.1:${STOCK_PORT}" \
      APP_SERVICE_FUNDING_URL="http://127.0.0.1:${FUNDING_PORT}" \
      APP_SERVICE_SALES_URL="http://127.0.0.1:${SALES_PORT}" \
      APP_SERVICE_HOT_DEAL_URL="http://127.0.0.1:${HOTDEAL_PORT}" \
      APP_SERVICE_SEARCH_URL="http://127.0.0.1:${SEARCH_PORT}" \
      ANALYTICS_DASHBOARD_SERVICE_URL="http://127.0.0.1:${ANALYTICS_PORT}" \
      ./gradlew :servers:gateways:client-gateway:bootRun --no-daemon >"$LOG_DIR/gateway.log" 2>&1
  ) &
  PIDS+=("$!")
}

run_publish_chain() {
  local ts keyword product_payload create_raw create_body create_status item_id
  local status_raw status_body status_code stock_item_id
  local purchase_payload purchase_raw purchase_body purchase_status purchase_id
  local cancel_raw cancel_body cancel_status
  local overview_raw overview_body overview_status

  ts="$(date +%s)"
  keyword="full-chain-${ts}"

  product_payload="$(cat <<JSON
{"title":"Full Chain Product ${keyword}","description":"full stack publish chain e2e","price":12345,"storeId":${STORE_ID},"options":[{"optionName":"default","additionalPrice":0,"stockQuantity":7}],"shippingInfo":{"shippingFee":2500,"freeShippingThreshold":30000,"estimatedDays":2,"returnPolicy":"returnable"}}
JSON
)"

  create_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${PRODUCT_PORT}/api/products" \
    -H "Content-Type: application/json" \
    -H "X-Gateway-Auth: ${INTERNAL_AUTH_TOKEN}" \
    -H "X-User-Id: ${SELLER_ID}" \
    -d "$product_payload")"
  create_body="$(extract_http_body "$create_raw")"
  create_status="$(extract_http_status "$create_raw")"
  ensure_http_status "product create status" "$create_status" "200"
  ensure_json_success "product create success" "$create_body"

  item_id="$(echo "$create_body" | jq -r '.data.id // empty')"
  if [[ -z "$item_id" || "$item_id" == "null" ]]; then
    fail "resolved created item id"
    return 1
  fi
  pass "resolved created item id (${item_id})"

  status_raw="$(curl -sS -w '\n%{http_code}' -X PATCH "http://127.0.0.1:${PRODUCT_PORT}/api/items/${item_id}/status" \
    -H "Content-Type: application/json" \
    -H "X-Gateway-Auth: ${INTERNAL_AUTH_TOKEN}" \
    -H "X-User-Id: ${SELLER_ID}" \
    -d '{"status":"ON_SALE","reason":"full chain e2e"}')"
  status_body="$(extract_http_body "$status_raw")"
  status_code="$(extract_http_status "$status_raw")"
  ensure_http_status "item status change DRAFT->ON_SALE" "$status_code" "200"
  ensure_json_success "item status change success" "$status_body"

  if ! stock_item_id="$(wait_stock_item_id "$item_id")"; then
    fail "stock item initialization from item-events"
    return 1
  fi
  pass "stock item initialization from item-events (stockItemId=${stock_item_id})"

  purchase_payload="$(cat <<JSON
{"itemId":${item_id},"stockItemId":${stock_item_id},"quantity":1}
JSON
)"

  purchase_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${SALES_PORT}/api/v1/sales/purchase" \
    -H "Content-Type: application/json" \
    -H "X-Gateway-Auth: ${INTERNAL_AUTH_TOKEN}" \
    -H "X-User-Id: ${BUYER_ID}" \
    -d "$purchase_payload")"
  purchase_body="$(extract_http_body "$purchase_raw")"
  purchase_status="$(extract_http_status "$purchase_raw")"
  ensure_http_status "sales purchase status" "$purchase_status" "200"
  ensure_json_success "sales purchase success" "$purchase_body"

  purchase_id="$(echo "$purchase_body" | jq -r '.data.id // empty')"
  if [[ -z "$purchase_id" || "$purchase_id" == "null" ]]; then
    fail "resolved purchase id"
    return 1
  fi
  pass "resolved purchase id (${purchase_id})"

  cancel_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${SALES_PORT}/internal/v1/sales/purchases/${purchase_id}/cancel?userId=${BUYER_ID}" \
    -H "X-Gateway-Auth: ${INTERNAL_AUTH_TOKEN}")"
  cancel_body="$(extract_http_body "$cancel_raw")"
  cancel_status="$(extract_http_status "$cancel_raw")"
  ensure_http_status "sales cancel status" "$cancel_status" "200"
  ensure_json_success "sales cancel success" "$cancel_body"

  wait_count_ge "product_db" \
    "SELECT COUNT(*) FROM outbox_messages WHERE aggregate_type='Item' AND aggregate_id='${item_id}' AND event_type IN ('ITEM_CREATED','ITEM_STATUS_CHANGED') AND status='PUBLISHED'" \
    2 \
    "product outbox published events"

  wait_count_eq "product_db" \
    "SELECT COUNT(*) FROM outbox_messages WHERE aggregate_type='Item' AND aggregate_id='${item_id}' AND status='FAILED'" \
    0 \
    "product outbox failed events"

  wait_count_ge "sales_db" \
    "SELECT COUNT(*) FROM outbox_messages WHERE aggregate_type='Purchase' AND aggregate_id='${purchase_id}' AND event_type IN ('PURCHASE_CREATED','PURCHASE_CANCELLED') AND status='PUBLISHED'" \
    2 \
    "sales outbox published events"

  wait_count_eq "sales_db" \
    "SELECT COUNT(*) FROM outbox_messages WHERE aggregate_type='Purchase' AND aggregate_id='${purchase_id}' AND status='FAILED'" \
    0 \
    "sales outbox failed events"

  wait_count_ge "analytics_db" \
    "SELECT COUNT(*) FROM analytics_dim_item_snapshot WHERE item_id=${item_id} AND store_id=${STORE_ID} AND seller_id=${SELLER_ID}" \
    1 \
    "analytics item snapshot ingested"

  wait_count_ge "analytics_db" \
    "SELECT COUNT(*) FROM analytics_raw_sales_event WHERE purchase_id=${purchase_id}" \
    2 \
    "analytics sales events ingested"

  overview_raw="$(curl -sS -w '\n%{http_code}' \
    "http://127.0.0.1:${GW_PORT}/bff/v1/seller/dashboard/overview?storeId=${STORE_ID}&mode=DAILY&date=${TARGET_DATE}" \
    -H "X-User-Id: ${SELLER_ID}" \
    -H "X-Store-Id: ${STORE_ID}")"
  overview_body="$(extract_http_body "$overview_raw")"
  overview_status="$(extract_http_status "$overview_raw")"
  ensure_http_status "gateway dashboard overview status" "$overview_status" "200"
  ensure_json_success "gateway dashboard overview success" "$overview_body"
  ensure_jq_true "gateway dashboard reflects sales chain" "$overview_body" '.data.sales.orderCount >= 1 and .data.sales.cancelCount >= 1 and .data.mode == "DAILY"'
  ensure_jq_true "gateway dashboard reflects item snapshot" "$overview_body" '.data.item.onSaleCount >= 1'
}

check_search_publish_gap() {
  local offset_before offset_after search_raw search_body search_status query

  offset_before="$(topic_offset_sum "search-events" | tr -d '[:space:]')"

  query="chain-gap-$(date +%s)"
  search_raw="$(curl -sS -w '\n%{http_code}' "http://127.0.0.1:${SEARCH_PORT}/api/v1/search?q=${query}&size=5" \
    -H "X-User-Id: ${SELLER_ID}" \
    -H "X-Store-Id: ${STORE_ID}" \
    -H "X-Session-Id: ${SEARCH_SESSION_ID}" || true)"
  search_body="$(extract_http_body "$search_raw")"
  search_status="$(extract_http_status "$search_raw")"

  if [[ "$search_status" == "200" ]] && [[ "$(echo "$search_body" | jq -r '.success // false')" == "true" ]]; then
    pass "search query endpoint call"
  else
    fail "search query endpoint call not successful (status=${search_status})"
    return 1
  fi

  sleep 2

  offset_after="$(topic_offset_sum "search-events" | tr -d '[:space:]')"
  if [[ "$offset_before" =~ ^[0-9]+$ && "$offset_after" =~ ^[0-9]+$ ]]; then
    if (( offset_after > offset_before )); then
      pass "search-events topic increased (${offset_before} -> ${offset_after})"
    else
      fail "search-events topic did not increase (${offset_before} -> ${offset_after})"
      return 1
    fi
  else
    fail "unable to read search-events topic offsets"
    return 1
  fi

  wait_count_ge "analytics_db" \
    "SELECT COUNT(*) FROM analytics_raw_search_event WHERE store_id=${STORE_ID} AND seller_id=${SELLER_ID} AND event_type='SEARCH_EXECUTED'" \
    1 \
    "analytics search events ingested"
}

main() {
  require_cmd curl
  require_cmd jq
  require_cmd docker
  require_cmd lsof

  check_port_free "$GW_PORT" || true
  check_port_free "$PRODUCT_PORT" || true
  check_port_free "$STOCK_PORT" || true
  check_port_free "$FUNDING_PORT" || true
  check_port_free "$SALES_PORT" || true
  check_port_free "$SEARCH_PORT" || true
  check_port_free "$HOTDEAL_PORT" || true
  check_port_free "$ANALYTICS_PORT" || true

  echo "[INFO] preparing docker infra"
  (cd "$ROOT/docker" && docker compose up -d postgres redis zookeeper kafka elasticsearch >/dev/null)
  pass "docker infra up (postgres/redis/zookeeper/kafka/elasticsearch)"

  wait_es_up
  ensure_analytics_db

  psql_exec analytics_db "TRUNCATE TABLE analytics_raw_sales_event RESTART IDENTITY CASCADE"
  psql_exec analytics_db "TRUNCATE TABLE analytics_raw_search_event RESTART IDENTITY CASCADE"
  psql_exec analytics_db "TRUNCATE TABLE analytics_dim_item_snapshot CASCADE"
  pass "analytics dashboard tables reset"

  pass "using unique Kafka consumer groups with auto-offset-reset=latest"

  start_services

  wait_health "stock-service" "$STOCK_PORT"
  wait_health "product-service" "$PRODUCT_PORT"
  wait_health "sales-service" "$SALES_PORT"
  wait_health "funding-service" "$FUNDING_PORT"
  wait_health "hot-deal-service" "$HOTDEAL_PORT"
  wait_health "search-service" "$SEARCH_PORT"
  wait_health "analytics-dashboard" "$ANALYTICS_PORT"
  wait_health "client-gateway" "$GW_PORT"

  wait_consumer_assignment "$STOCK_CONSUMER_GROUP" "item-events" "stock consumer assignment ready (item-events)"
  wait_consumer_assignment "$STOCK_CONSUMER_GROUP" "payment-events" "stock consumer assignment ready (payment-events)"
  wait_consumer_assignment "$ANALYTICS_CONSUMER_GROUP" "item-events" "analytics consumer assignment ready (item-events)"
  wait_consumer_assignment "$ANALYTICS_CONSUMER_GROUP" "sales-events" "analytics consumer assignment ready (sales-events)"
  wait_consumer_assignment "$ANALYTICS_CONSUMER_GROUP" "search-events" "analytics consumer assignment ready (search-events)"

  run_publish_chain
  check_search_publish_gap

  echo "[INFO] summary: passes=${PASSES}, failures=${FAILURES}, warnings=${WARNINGS}"
  if (( FAILURES > 0 )); then
    exit 1
  fi
}

main "$@"
