#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT/docker/scripts/lib/e2e_support.sh"
ensure_java21
LOG_DIR="${LOG_DIR:-/tmp/outbox_broker_recovery_e2e_logs_$(date +%Y%m%d_%H%M%S)}"
mkdir -p "$LOG_DIR"

STORE_PORT="${STORE_PORT:-18472}"
PRODUCT_PORT="${PRODUCT_PORT:-18484}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-project03-postgres}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-project03-kafka}"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/.gradle-codex-e2e}"

SELLER_ID="${SELLER_ID:-640001}"
STORE_ID="${STORE_ID:-650001}"

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

wait_kafka_ready() {
  for _ in {1..90}; do
    if docker exec -i "$KAFKA_CONTAINER" kafka-topics --bootstrap-server localhost:9092 --list >/dev/null 2>&1; then
      pass "kafka is ready"
      return 0
    fi
    sleep 1
  done
  fail "kafka readiness timeout"
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

psql_query() {
  local db="$1"
  local sql="$2"
  docker exec -i "$POSTGRES_CONTAINER" psql -U postgres -d "$db" -At -c "$sql"
}

topic_offset_sum() {
  local topic="$1"
  local offsets
  offsets="$(docker exec -i "$KAFKA_CONTAINER" bash -lc "kafka-run-class kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic ${topic} --time -1 2>/dev/null" || true)"
  echo "$offsets" | awk -F: '{sum+=$3} END {print sum+0}'
}

get_outbox_status() {
  local item_id="$1"
  psql_query product_db \
    "SELECT status FROM outbox_messages WHERE aggregate_type='Item' AND aggregate_id='${item_id}' AND event_type='ITEM_CREATED' ORDER BY created_at DESC LIMIT 1;" | tr -d '[:space:]'
}

wait_outbox_status() {
  local item_id="$1"
  local expected="$2"
  local timeout_seconds="${3:-120}"
  for _ in $(seq 1 "$timeout_seconds"); do
    local status
    status="$(get_outbox_status "$item_id")"
    if [[ "$status" == "$expected" ]]; then
      pass "outbox status=${expected} (itemId=${item_id})"
      return 0
    fi
    sleep 1
  done
  fail "outbox status=${expected} timeout (itemId=${item_id})"
  return 1
}

wait_outbox_non_published() {
  local item_id="$1"
  local timeout_seconds="${2:-60}"
  for _ in $(seq 1 "$timeout_seconds"); do
    local status
    status="$(get_outbox_status "$item_id")"
    if [[ "$status" == "PENDING" || "$status" == "SENDING" || "$status" == "FAILED" ]]; then
      pass "outbox captured non-published status=${status} during broker outage (itemId=${item_id})"
      return 0
    fi
    sleep 1
  done
  fail "outbox non-published status not observed during broker outage (itemId=${item_id})"
  return 1
}

start_product() {
  echo "[INFO] starting product-service on ${PRODUCT_PORT}"
  (
    cd "$ROOT"
    env \
      GRADLE_USER_HOME="$GRADLE_USER_HOME" \
      SERVER_PORT="$PRODUCT_PORT" \
      STORE_SERVICE_URL="http://127.0.0.1:${STORE_PORT}" \
      APP_GATEWAY_SECURITY_ENABLED=false \
      APP_OUTBOX_RELAY_FIXED_DELAY_MS=1000 \
      APP_OUTBOX_RELAY_FETCH_BEFORE_SECONDS=1 \
      APP_OUTBOX_RELAY_MAX_IN_FLIGHT=8 \
      APP_OUTBOX_RELAY_SENDING_STALE_THRESHOLD_SECONDS=15 \
      ./gradlew :servers:services:product:bootRun --no-daemon >"$LOG_DIR/product.log" 2>&1
  ) &
  PIDS+=("$!")
}

create_product() {
  local label="$1"
  local ts payload raw body status item_id

  ts="$(date +%s)"
  payload="$(cat <<JSON
{"title":"Outbox ${label} ${ts}","description":"outbox broker recovery e2e","price":9900,"storeId":${STORE_ID},"options":[{"optionName":"기본","additionalPrice":0,"stockQuantity":3}],"shippingInfo":{"shippingFee":2500,"freeShippingThreshold":30000,"estimatedDays":2,"returnPolicy":"returnable"}}
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
    fail "created item id resolved (${label})"
    return 1
  fi

  pass "created item id resolved (${label}, itemId=${item_id})"
  CREATED_ITEM_ID="$item_id"
  return 0
}

main() {
  require_cmd curl
  require_cmd jq
  require_cmd docker
  require_cmd lsof

  check_port_free "$PRODUCT_PORT" || true
  check_port_free "$STORE_PORT" || true

  echo "[INFO] preparing docker infra"
  (cd "$ROOT/docker" && docker compose up -d postgres redis zookeeper kafka >/dev/null)
  pass "docker infra up (postgres/redis/zookeeper/kafka)"
  wait_kafka_ready

  echo "[INFO] starting store-service on ${STORE_PORT}"
  store_pid="$(start_store_service "$ROOT" "$LOG_DIR/store.log" "$STORE_PORT" "$GRADLE_USER_HOME")"
  PIDS+=("$store_pid")

  wait_health "store-service" "$STORE_PORT"
  start_product
  wait_health "product-service" "$PRODUCT_PORT"

  STORE_ID="$(ensure_store_fixture "$STORE_PORT" "$SELLER_ID" "Outbox Recovery Store ${SELLER_ID}" "Seoul Outbox-ro 1" "010-0000-1006" "outbox broker recovery e2e store")" || {
    fail "store fixture ready"
    exit 1
  }
  pass "store fixture ready (storeId=${STORE_ID})"

  local baseline_item outage_item offset_before offset_after
  create_product "baseline"
  baseline_item="$CREATED_ITEM_ID"
  wait_outbox_status "$baseline_item" "PUBLISHED" 120

  offset_before="$(topic_offset_sum "item-events" | tr -d '[:space:]')"
  if [[ "$offset_before" =~ ^[0-9]+$ ]]; then
    pass "item-events offset captured before outage (${offset_before})"
  else
    fail "item-events offset read before outage"
    return 1
  fi

  echo "[INFO] stopping kafka for broker outage injection"
  (cd "$ROOT/docker" && docker compose stop kafka >/dev/null)
  pass "kafka stopped for outage case"

  create_product "kafka-outage"
  outage_item="$CREATED_ITEM_ID"
  wait_outbox_non_published "$outage_item" 90

  echo "[INFO] recovering kafka broker"
  (cd "$ROOT/docker" && docker compose up -d kafka >/dev/null)
  wait_kafka_ready

  wait_outbox_status "$outage_item" "PUBLISHED" 180

  offset_after="$(topic_offset_sum "item-events" | tr -d '[:space:]')"
  if [[ "$offset_after" =~ ^[0-9]+$ ]] && (( offset_after > offset_before )); then
    pass "item-events offset increased after broker recovery (${offset_before} -> ${offset_after})"
  else
    fail "item-events offset did not increase after broker recovery (${offset_before} -> ${offset_after})"
  fi

  echo "[INFO] summary: passes=${PASSES}, failures=${FAILURES}"
  if (( FAILURES > 0 )); then
    exit 1
  fi
}

main "$@"
