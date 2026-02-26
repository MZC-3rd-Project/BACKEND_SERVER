#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOG_DIR="${LOG_DIR:-/tmp/gateway_seller_dashboard_e2e_logs_$(date +%Y%m%d_%H%M%S)}"
mkdir -p "$LOG_DIR"

GW_PORT="${GW_PORT:-18180}"
ANALYTICS_PORT="${ANALYTICS_PORT:-18095}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-project03-postgres}"

STORE_ID="${STORE_ID:-920001}"
SELLER_ID="${SELLER_ID:-910001}"
TARGET_DATE="${TARGET_DATE:-2026-02-26}"
INTERNAL_AUTH_TOKEN="${INTERNAL_AUTH_TOKEN:-gw-dashboard-e2e-token}"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/.gradle-codex-e2e}"

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
  for _ in {1..180}; do
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

wait_bff_ready() {
  local url="http://127.0.0.1:${GW_PORT}/bff/v1/seller/dashboard/overview?mode=DAILY&date=${TARGET_DATE}"
  for _ in {1..180}; do
    local status
    status="$(curl -sS -o /dev/null -w "%{http_code}" --max-time 2 "$url" || true)"
    if [[ "$status" == "400" || "$status" == "200" ]]; then
      pass "client-gateway BFF endpoint is ready"
      return 0
    fi
    sleep 1
  done
  fail "client-gateway BFF readiness timeout"
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
    fail "$label"
  fi
}

ensure_jq_true() {
  local label="$1"
  local body="$2"
  local expr="$3"
  if echo "$body" | jq -e "$expr" >/dev/null; then
    pass "$label"
  else
    fail "$label (expr: $expr)"
  fi
}

psql_exec() {
  local db="$1"
  local sql="$2"
  docker exec -i "$POSTGRES_CONTAINER" psql -U postgres -d "$db" -v ON_ERROR_STOP=1 -c "$sql" >/dev/null
}

ensure_infra() {
  (cd "$ROOT/docker" && docker compose up -d postgres redis zookeeper kafka >/dev/null)
  pass "docker infra up (postgres/redis/zookeeper/kafka)"
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

seed_analytics_data() {
  psql_exec analytics_db "TRUNCATE TABLE analytics_raw_sales_event RESTART IDENTITY CASCADE"
  psql_exec analytics_db "TRUNCATE TABLE analytics_raw_search_event RESTART IDENTITY CASCADE"
  psql_exec analytics_db "TRUNCATE TABLE analytics_dim_item_snapshot CASCADE"

  psql_exec analytics_db "
INSERT INTO analytics_dim_item_snapshot (
  item_id, store_id, seller_id, item_type, item_status, price, stock_quantity, snapshot_at, created_at, updated_at, deleted_at
) VALUES
  (11001, ${STORE_ID}, ${SELLER_ID}, 'PRODUCT', 'ON_SALE', 10000, 12, '${TARGET_DATE} 09:00:00', NOW(), NOW(), NULL),
  (11002, ${STORE_ID}, ${SELLER_ID}, 'PRODUCT', 'SOLD_OUT', 12000, 0, '${TARGET_DATE} 09:00:00', NOW(), NOW(), NULL),
  (11003, ${STORE_ID}, ${SELLER_ID}, 'PRODUCT', 'HIDDEN', 8000, 3, '${TARGET_DATE} 09:00:00', NOW(), NOW(), NULL)
"

  psql_exec analytics_db "
INSERT INTO analytics_raw_sales_event (
  event_id, event_type, store_id, seller_id, item_id, order_id, purchase_id, quantity,
  gross_amount, net_amount, occurred_at, ingested_at, created_at, updated_at, deleted_at
) VALUES
  ('sales-e2e-1', 'PURCHASE_CREATED', ${STORE_ID}, ${SELLER_ID}, 11001, 70001, 80001, 1, 10000, 9000, '${TARGET_DATE} 10:00:00', '${TARGET_DATE} 10:00:01', NOW(), NOW(), NULL),
  ('sales-e2e-2', 'PURCHASE_CREATED', ${STORE_ID}, ${SELLER_ID}, 11002, 70002, 80002, 1, 5000, 4500, '${TARGET_DATE} 11:00:00', '${TARGET_DATE} 11:00:01', NOW(), NOW(), NULL),
  ('sales-e2e-3', 'PURCHASE_CANCELLED', ${STORE_ID}, ${SELLER_ID}, 11002, 70002, 80002, 1, 0, -2000, '${TARGET_DATE} 12:00:00', '${TARGET_DATE} 12:00:01', NOW(), NOW(), NULL),
  ('sales-e2e-4', 'PURCHASE_REFUNDED', ${STORE_ID}, ${SELLER_ID}, 11003, 70003, 80003, 1, 0, -1000, '${TARGET_DATE} 13:00:00', '${TARGET_DATE} 13:00:01', NOW(), NOW(), NULL)
"

  psql_exec analytics_db "
INSERT INTO analytics_raw_search_event (
  event_id, event_type, store_id, seller_id, item_id, query_hash, session_id,
  occurred_at, ingested_at, created_at, updated_at, deleted_at
) VALUES
  ('search-e2e-1', 'SEARCH_EXECUTED', ${STORE_ID}, ${SELLER_ID}, NULL, 'hash-1', 'session-1', '${TARGET_DATE} 10:30:00', '${TARGET_DATE} 10:30:01', NOW(), NOW(), NULL),
  ('search-e2e-2', 'SEARCH_EXECUTED', ${STORE_ID}, ${SELLER_ID}, NULL, 'hash-2', 'session-2', '${TARGET_DATE} 10:31:00', '${TARGET_DATE} 10:31:01', NOW(), NOW(), NULL),
  ('search-e2e-3', 'SEARCH_ITEM_CLICKED', ${STORE_ID}, ${SELLER_ID}, 11001, 'hash-1', 'session-1', '${TARGET_DATE} 10:32:00', '${TARGET_DATE} 10:32:01', NOW(), NOW(), NULL)
"

  pass "seeded analytics dashboard test data"
}

start_analytics_service() {
  (
    cd "$ROOT"
    env \
      GRADLE_USER_HOME="$GRADLE_USER_HOME" \
      SERVER_PORT="$ANALYTICS_PORT" \
      DB_URL="jdbc:postgresql://127.0.0.1:5432/analytics_db" \
      DB_USERNAME="postgres" \
      DB_PASSWORD="postgres" \
      SPRING_PROFILES_ACTIVE="local" \
      SPRING_KAFKA_LISTENER_AUTO_STARTUP="false" \
      APP_GATEWAY_SECURITY_ENABLED="true" \
      APP_GATEWAY_SECURITY_GATEWAY_AUTH_ENABLED="true" \
      APP_GATEWAY_SECURITY_INTERNAL_AUTH_HEADER="X-Gateway-Auth" \
      APP_GATEWAY_SECURITY_INTERNAL_AUTH_TOKEN="$INTERNAL_AUTH_TOKEN" \
      ./gradlew :servers:services:analytics-dashboard:bootRun --no-daemon >"$LOG_DIR/analytics-dashboard.log" 2>&1
  ) &
  PIDS+=("$!")
  pass "analytics-dashboard bootRun started"
}

start_gateway_service() {
  (
    cd "$ROOT"
    env \
      GRADLE_USER_HOME="$GRADLE_USER_HOME" \
      SERVER_PORT="$GW_PORT" \
      ANALYTICS_DASHBOARD_SERVICE_URL="http://127.0.0.1:${ANALYTICS_PORT}" \
      GATEWAY_AUTH_ENABLED="false" \
      GATEWAY_SESSION_ENABLED="false" \
      APP_SECURITY_CONTEXT_PARSER_ENABLED="false" \
      GATEWAY_SECURITY_INTERNAL_AUTH_HEADER="X-Gateway-Auth" \
      GATEWAY_SECURITY_INTERNAL_AUTH_TOKEN="$INTERNAL_AUTH_TOKEN" \
      ./gradlew :servers:gateways:client-gateway:bootRun --no-daemon >"$LOG_DIR/client-gateway.log" 2>&1
  ) &
  PIDS+=("$!")
  pass "client-gateway bootRun started"
}

run_assertions() {
  local raw body status

  raw="$(curl -sS -w '\n%{http_code}' \
    "http://127.0.0.1:${ANALYTICS_PORT}/internal/v1/analytics/stores/${STORE_ID}/dashboard/overview?mode=DAILY&date=${TARGET_DATE}" \
    -H "X-Gateway-Auth: ${INTERNAL_AUTH_TOKEN}" \
    -H "X-User-Id: ${SELLER_ID}" \
    -H "X-Store-Id: ${STORE_ID}")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "internal overview status" "$status" "200"
  ensure_json_success "internal overview success payload" "$body"
  ensure_jq_true "internal KPI aggregation" "$body" '.data.sales.grossSales == 15000 and .data.sales.netSales == 10500 and .data.sales.orderCount == 2 and .data.sales.cancelCount == 1 and .data.sales.refundCount == 1'
  ensure_jq_true "internal search KPI aggregation" "$body" '.data.search.searchCount == 2 and .data.search.clickCount == 1 and .data.search.ctr == 0.5'
  ensure_jq_true "internal item KPI aggregation" "$body" '.data.item.onSaleCount == 1 and .data.item.soldOutCount == 1 and .data.item.hiddenCount == 1'
  ensure_jq_true "internal series daily bucket" "$body" ".data.series | length == 1 and .[0].bucketStart == \"${TARGET_DATE}\" and .[0].grossSales == 15000 and .[0].netSales == 10500 and .[0].orderCount == 2"
  ensure_jq_true "internal store/review scope placeholders are null" "$body" '.data.extensions.store == null and .data.extensions.review == null'

  raw="$(curl -sS -w '\n%{http_code}' \
    "http://127.0.0.1:${GW_PORT}/bff/v1/seller/dashboard/overview?mode=DAILY&date=${TARGET_DATE}" \
    -H "X-User-Id: ${SELLER_ID}" \
    -H "X-Store-Id: ${STORE_ID}")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "gateway overview status" "$status" "200"
  ensure_json_success "gateway overview success payload" "$body"
  ensure_jq_true "gateway relays aggregated sales" "$body" '.data.sales.grossSales == 15000 and .data.sales.netSales == 10500 and .data.mode == "DAILY"'
  ensure_jq_true "gateway queryRange/date" "$body" ".data.queryRange.from == \"${TARGET_DATE}\" and .data.queryRange.to == \"${TARGET_DATE}\" and .data.queryRange.bucket == \"DAY\""
  ensure_jq_true "gateway store/review scope placeholders are null" "$body" '.data.extensions.store == null and .data.extensions.review == null'

  raw="$(curl -sS -w '\n%{http_code}' \
    "http://127.0.0.1:${GW_PORT}/bff/v1/seller/dashboard/overview?storeId=-1&mode=DAILY&date=${TARGET_DATE}" \
    -H "X-User-Id: ${SELLER_ID}")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "gateway invalid storeId status" "$status" "400"
  ensure_jq_true "gateway invalid storeId code" "$body" '.success == false and .error.code == "BFF-DASHBOARD-400"'
}

main() {
  require_cmd curl
  require_cmd jq
  require_cmd docker
  require_cmd lsof

  check_port_free "$ANALYTICS_PORT" || true
  check_port_free "$GW_PORT" || true

  ensure_infra
  ensure_analytics_db

  start_analytics_service
  wait_health "analytics-dashboard" "$ANALYTICS_PORT"

  seed_analytics_data

  start_gateway_service
  wait_health "client-gateway" "$GW_PORT"
  wait_bff_ready

  run_assertions

  echo "[INFO] e2e summary: passes=${PASSES}, failures=${FAILURES}"
  if (( FAILURES > 0 )); then
    exit 1
  fi
}

main "$@"
