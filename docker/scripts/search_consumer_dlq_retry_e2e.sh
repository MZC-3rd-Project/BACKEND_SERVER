#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT/docker/scripts/lib/e2e_support.sh"
ensure_java21
LOG_DIR="${LOG_DIR:-/tmp/search_consumer_dlq_retry_e2e_logs_$(date +%Y%m%d_%H%M%S)}"
mkdir -p "$LOG_DIR"

STORE_PORT="${STORE_PORT:-18872}"
PRODUCT_PORT="${PRODUCT_PORT:-18884}"
SEARCH_PORT="${SEARCH_PORT:-18888}"
ELASTICSEARCH_PORT="${ELASTICSEARCH_PORT:-23173}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-project03-postgres}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-project03-kafka}"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/.gradle-codex-e2e}"

SELLER_ID="${SELLER_ID:-660001}"
STORE_ID="${STORE_ID:-670001}"
NOW_TS="$(date +%s)"
CONSUMER_GROUP_SUFFIX="${CONSUMER_GROUP_SUFFIX:-$NOW_TS}"
SEARCH_CONSUMER_GROUP="${SEARCH_CONSUMER_GROUP:-search-dlq-retry-e2e-${CONSUMER_GROUP_SUFFIX}}"
PRODUCT_CONSUMER_GROUP="${PRODUCT_CONSUMER_GROUP:-product-dlq-retry-e2e-${CONSUMER_GROUP_SUFFIX}}"

PIDS=()
PASSES=0
FAILURES=0
CREATED_ITEM_ID=""

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

  fail "${label} timeout"
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

wait_count_gt() {
  local db="$1"
  local sql="$2"
  local baseline="$3"
  local label="$4"
  local timeout_seconds="${5:-120}"

  for _ in $(seq 1 "$timeout_seconds"); do
    local value
    value="$(psql_query "$db" "$sql" 2>/dev/null | tr -d '[:space:]' || true)"
    if [[ "$value" =~ ^[0-9]+$ ]] && (( value > baseline )); then
      pass "$label (baseline=${baseline}, value=${value})"
      return 0
    fi
    sleep 1
  done

  fail "$label timeout (baseline=${baseline})"
  return 1
}

wait_count_eq() {
  local db="$1"
  local sql="$2"
  local expected="$3"
  local label="$4"
  local timeout_seconds="${5:-60}"

  for _ in $(seq 1 "$timeout_seconds"); do
    local value
    value="$(psql_query "$db" "$sql" 2>/dev/null | tr -d '[:space:]' || true)"
    if [[ "$value" =~ ^[0-9]+$ ]] && (( value == expected )); then
      pass "$label (value=${value})"
      return 0
    fi
    sleep 1
  done

  fail "$label timeout (expected=${expected})"
  return 1
}

get_outbox_event_id() {
  local item_id="$1"
  psql_query product_db \
    "SELECT event_id FROM outbox_messages WHERE aggregate_type='Item' AND aggregate_id='${item_id}' AND event_type='ITEM_CREATED' ORDER BY created_at DESC LIMIT 1;" | tr -d '[:space:]'
}

wait_processed_event_status() {
  local event_id="$1"
  local expected_status="$2"
  local timeout_seconds="${3:-120}"

  for _ in $(seq 1 "$timeout_seconds"); do
    local status
    status="$(psql_query search_db "SELECT status FROM processed_events WHERE event_id='${event_id}' LIMIT 1;" | tr -d '[:space:]')"
    if [[ "$status" == "$expected_status" ]]; then
      pass "processed_events status=${expected_status} (eventId=${event_id})"
      return 0
    fi
    sleep 1
  done

  fail "processed_events status=${expected_status} timeout (eventId=${event_id})"
  return 1
}

recreate_search_index() {
  local attempt max_attempts raw body status success
  max_attempts=10
  for attempt in $(seq 1 "$max_attempts"); do
    raw="$(curl -sS -w '\n%{http_code}' -X POST \
      "http://127.0.0.1:${SEARCH_PORT}/internal/v1/search/indexes/recreate?indexName=items")"
    body="$(extract_http_body "$raw")"
    status="$(extract_http_status "$raw")"
    success="$(echo "$body" | jq -r '.success // false' 2>/dev/null || true)"
    if [[ "$status" == "200" && "$success" == "true" ]]; then
      pass "search index recreate success"
      return 0
    fi
    sleep 1
  done
  fail "search index recreate failed"
  return 1
}

create_product() {
  local label="$1"
  local title="$2"
  local payload raw body status item_id

  payload="$(cat <<JSON
{"title":"${title}","description":"${label}","price":15000,"storeId":${STORE_ID},"options":[{"optionName":"기본","additionalPrice":0,"stockQuantity":5}],"shippingInfo":{"shippingFee":2500,"freeShippingThreshold":30000,"estimatedDays":2,"returnPolicy":"returnable"}}
JSON
)"
  raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${PRODUCT_PORT}/api/products" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: ${SELLER_ID}" \
    -d "$payload")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "product create status (${label})" "$status" "200" "$body" || return 1
  ensure_json_success "product create success (${label})" "$body" || return 1

  item_id="$(echo "$body" | jq -r '.data.id // empty')"
  if [[ -z "$item_id" || "$item_id" == "null" ]]; then
    fail "product id resolved (${label})"
    return 1
  fi
  pass "product id resolved (${label}, itemId=${item_id})"
  CREATED_ITEM_ID="$item_id"
  return 0
}

start_services() {
  echo "[INFO] starting store-service on ${STORE_PORT}"
  store_pid="$(start_store_service "$ROOT" "$LOG_DIR/store.log" "$STORE_PORT" "$GRADLE_USER_HOME")"
  PIDS+=("$store_pid")

  echo "[INFO] starting product-service on ${PRODUCT_PORT}"
  (
    cd "$ROOT"
    env \
      GRADLE_USER_HOME="$GRADLE_USER_HOME" \
      SERVER_PORT="$PRODUCT_PORT" \
      SPRING_KAFKA_CONSUMER_GROUP_ID="$PRODUCT_CONSUMER_GROUP" \
      SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET=latest \
      STORE_SERVICE_URL="http://127.0.0.1:${STORE_PORT}" \
      APP_GATEWAY_SECURITY_ENABLED=false \
      ./gradlew :servers:services:product:bootRun --no-daemon >"$LOG_DIR/product.log" 2>&1
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
      APP_KAFKA_CONSUMER_MAX_RETRY_ATTEMPTS=2 \
      APP_KAFKA_CONSUMER_INITIAL_BACKOFF_MS=200 \
      APP_KAFKA_CONSUMER_BACKOFF_MULTIPLIER=1 \
      APP_KAFKA_CONSUMER_MAX_BACKOFF_MS=400 \
      ./gradlew :servers:services:search:bootRun --no-daemon >"$LOG_DIR/search.log" 2>&1
  ) &
  PIDS+=("$!")
}

main() {
  require_cmd curl
  require_cmd jq
  require_cmd docker
  require_cmd lsof

  check_port_free "$PRODUCT_PORT"
  check_port_free "$STORE_PORT"
  check_port_free "$SEARCH_PORT"

  echo "[INFO] preparing infra"
  (cd "$ROOT/docker" && docker compose up -d postgres redis zookeeper kafka elasticsearch >/dev/null)
  wait_es_up

  start_services
  wait_health "store-service" "$STORE_PORT"
  wait_health "product-service" "$PRODUCT_PORT"
  wait_health "search-service" "$SEARCH_PORT"
  recreate_search_index
  wait_consumer_assignment "$SEARCH_CONSUMER_GROUP" "item-events" "search consumer assignment ready (item-events)"

  STORE_ID="$(ensure_store_fixture "$STORE_PORT" "$SELLER_ID" "Search Retry Store ${SELLER_ID}" "Seoul Search-ro 1" "010-0000-1005" "search dlq retry e2e store")" || {
    fail "store fixture ready"
    exit 1
  }
  pass "store fixture ready (storeId=${STORE_ID})"

  psql_exec search_db "TRUNCATE TABLE dead_letter_messages RESTART IDENTITY CASCADE;"
  psql_exec search_db "TRUNCATE TABLE processed_events RESTART IDENTITY CASCADE;"
  pass "search dead-letter/processed tables reset"

  local dlq_before dlq_after item_fail item_ok recovery_event_id
  dlq_before="$(psql_query search_db "SELECT count(*) FROM dead_letter_messages WHERE topic='item-events';" | tr -d '[:space:]')"
  if [[ ! "$dlq_before" =~ ^[0-9]+$ ]]; then
    fail "failed to read initial DLQ count"
    exit 1
  fi
  pass "initial item-events DLQ count=${dlq_before}"

  echo "[INFO] stopping elasticsearch for failure injection"
  (cd "$ROOT/docker" && docker compose stop elasticsearch >/dev/null)
  pass "elasticsearch stopped"

  create_product "dlq-failure" "search-dlq-failure-${NOW_TS}"
  item_fail="$CREATED_ITEM_ID"
  wait_count_gt "search_db" \
    "SELECT count(*) FROM dead_letter_messages WHERE topic='item-events' AND event_type='ITEM_CREATED';" \
    "$dlq_before" \
    "DLQ row created after retry exhaustion" \
    120

  dlq_after="$(psql_query search_db "SELECT count(*) FROM dead_letter_messages WHERE topic='item-events';" | tr -d '[:space:]')"
  pass "item-events DLQ count after failure=${dlq_after}"
  pass "retry limit scenario uses app.kafka.consumer.maxRetryAttempts=2"

  echo "[INFO] recovering elasticsearch"
  (cd "$ROOT/docker" && docker compose up -d elasticsearch >/dev/null)
  wait_es_up
  recreate_search_index

  create_product "recovery" "search-dlq-recover-${NOW_TS}"
  item_ok="$CREATED_ITEM_ID"
  recovery_event_id="$(get_outbox_event_id "$item_ok")"
  if [[ -z "$recovery_event_id" ]]; then
    fail "recovery item outbox event id resolved"
    exit 1
  fi
  pass "recovery item outbox event id resolved (${recovery_event_id})"
  wait_processed_event_status "$recovery_event_id" "PROCESSED" 120
  wait_count_eq "search_db" \
    "SELECT count(*) FROM dead_letter_messages WHERE topic='item-events';" \
    "$dlq_after" \
    "no additional DLQ rows after elasticsearch recovery" \
    60

  echo "[INFO] summary: passes=${PASSES}, failures=${FAILURES}"
  if (( FAILURES > 0 )); then
    exit 1
  fi
}

main "$@"
