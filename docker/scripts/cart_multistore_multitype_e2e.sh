#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOG_DIR="${LOG_DIR:-/tmp/cart_multistore_multitype_e2e_$(date +%Y%m%d_%H%M%S)}"
mkdir -p "$LOG_DIR"

PROFILE_PORT="${PROFILE_PORT:-18071}"
STORE_PORT="${STORE_PORT:-18072}"
PRODUCT_PORT="${PRODUCT_PORT:-18084}"
STOCK_PORT="${STOCK_PORT:-18085}"
SALES_PORT="${SALES_PORT:-18087}"
ORDER_PORT="${ORDER_PORT:-18090}"
STORE_QUERY_PORT="${STORE_QUERY_PORT:-18091}"
CART_PORT="${CART_PORT:-18096}"
POSTGRES_PORT="${POSTGRES_PORT:-5433}"
REDIS_PORT="${REDIS_PORT:-6379}"
KAFKA_PORT="${KAFKA_PORT:-29092}"
DYNAMODB_PORT="${DYNAMODB_PORT:-8000}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-project03-postgres}"
DYNAMODB_TABLE_NAME="${CART_DYNAMODB_TABLE_NAME:-donmoa-local-cart-items}"

JDK21_DEFAULT="/Users/ddingjoo/Library/Java/JavaVirtualMachines/corretto-21.0.10/Contents/Home"
if [[ -z "${JAVA_HOME:-}" && -d "$JDK21_DEFAULT" ]]; then
  export JAVA_HOME="$JDK21_DEFAULT"
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/.gradle-cart-multistore-e2e}"
NOW_TS="$(date +%s)"

PROFILE_CONSUMER_GROUP="${PROFILE_CONSUMER_GROUP:-profile-cart-e2e-${NOW_TS}}"
STORE_CONSUMER_GROUP="${STORE_CONSUMER_GROUP:-store-cart-e2e-${NOW_TS}}"
PRODUCT_CONSUMER_GROUP="${PRODUCT_CONSUMER_GROUP:-product-cart-e2e-${NOW_TS}}"
STOCK_CONSUMER_GROUP="${STOCK_CONSUMER_GROUP:-stock-cart-e2e-${NOW_TS}}"
ORDER_CONSUMER_GROUP="${ORDER_CONSUMER_GROUP:-order-cart-e2e-${NOW_TS}}"
SALES_CONSUMER_GROUP="${SALES_CONSUMER_GROUP:-sales-cart-e2e-${NOW_TS}}"
STORE_QUERY_CONSUMER_GROUP="${STORE_QUERY_CONSUMER_GROUP:-store-query-cart-e2e-${NOW_TS}}"

SELLER_ID="${SELLER_ID:-510001}"
SELLER_2_ID="${SELLER_2_ID:-510002}"
BUYER_ID="${BUYER_ID:-510003}"
SELLER_EMAIL="${SELLER_EMAIL:-cart-seller-${NOW_TS}@example.com}"
SELLER_2_EMAIL="${SELLER_2_EMAIL:-cart-seller-2-${NOW_TS}@example.com}"
OWNER_NICKNAME="${OWNER_NICKNAME:-cart-owner-${NOW_TS}}"
OWNER_2_NICKNAME="${OWNER_2_NICKNAME:-cart-owner-2-${NOW_TS}}"
STORE_NAME="${STORE_NAME:-cart-store-${NOW_TS}}"
STORE_2_NAME="${STORE_2_NAME:-cart-stage-${NOW_TS}}"
STORE_DESCRIPTION="${STORE_DESCRIPTION:-cart e2e store ${NOW_TS}}"
STORE_2_DESCRIPTION="${STORE_2_DESCRIPTION:-cart e2e store 2 ${NOW_TS}}"
STORE_ADDRESS="${STORE_ADDRESS:-Seoul Gangnam ${NOW_TS}}"
STORE_2_ADDRESS="${STORE_2_ADDRESS:-Seoul Jongno ${NOW_TS}}"
STORE_CONTACT="${STORE_CONTACT:-0101111${NOW_TS: -4}}"
STORE_2_CONTACT="${STORE_2_CONTACT:-0102222${NOW_TS: -4}}"

GOODS_TITLE="${GOODS_TITLE:-cart-goods-${NOW_TS}}"
GOODS_DESCRIPTION="${GOODS_DESCRIPTION:-cart goods description ${NOW_TS}}"
GOODS_OPTION_NAME="${GOODS_OPTION_NAME:-basic}"
GOODS_PRICE="${GOODS_PRICE:-10000}"
GOODS_OPTION_EXTRA_PRICE="${GOODS_OPTION_EXTRA_PRICE:-1500}"
GOODS_STOCK_TOTAL="${GOODS_STOCK_TOTAL:-12}"
GOODS_FINAL_QUANTITY="${GOODS_FINAL_QUANTITY:-4}"
GOODS_LINE_AMOUNT="$(((GOODS_PRICE + GOODS_OPTION_EXTRA_PRICE) * GOODS_FINAL_QUANTITY))"

PERFORMANCE_TITLE="${PERFORMANCE_TITLE:-cart-performance-${NOW_TS}}"
PERFORMANCE_DESCRIPTION="${PERFORMANCE_DESCRIPTION:-cart performance description ${NOW_TS}}"
PERFORMANCE_VENUE="${PERFORMANCE_VENUE:-Cart Hall ${NOW_TS}}"
PERFORMANCE_DATE="${PERFORMANCE_DATE:-2030-01-10}"
PERFORMANCE_TIME="${PERFORMANCE_TIME:-19:30:00}"
PERFORMANCE_TOTAL_SEATS="${PERFORMANCE_TOTAL_SEATS:-120}"
PERFORMANCE_GRADE_NAME="${PERFORMANCE_GRADE_NAME:-R}"
PERFORMANCE_GRADE_PRICE="${PERFORMANCE_GRADE_PRICE:-65000}"
PERFORMANCE_GRADE_TOTAL="${PERFORMANCE_GRADE_TOTAL:-20}"
PERFORMANCE_GRADE_FUNDING="${PERFORMANCE_GRADE_FUNDING:-0}"
PERFORMANCE_FINAL_QUANTITY="${PERFORMANCE_FINAL_QUANTITY:-3}"
PERFORMANCE_LINE_AMOUNT="$((PERFORMANCE_GRADE_PRICE * PERFORMANCE_FINAL_QUANTITY))"

EXPECTED_TOTAL="$((GOODS_LINE_AMOUNT + PERFORMANCE_LINE_AMOUNT))"
IDEMPOTENCY_KEY="${IDEMPOTENCY_KEY:-cart-e2e-${NOW_TS}}"

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

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "[ERROR] required command not found: $cmd" >&2
    exit 1
  fi
}

cleanup() {
  for pid in "${PIDS[@]:-}"; do
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
    fi
  done
  sleep 1
  for pid in "${PIDS[@]:-}"; do
    if kill -0 "$pid" 2>/dev/null; then
      kill -9 "$pid" 2>/dev/null || true
    fi
  done
  echo "[INFO] logs: $LOG_DIR"
}
trap cleanup EXIT

check_port_free() {
  local port="$1"
  if lsof -tiTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "[ERROR] port ${port} is already in use" >&2
    exit 1
  fi
  pass "port ${port} is free"
}

wait_container_health() {
  local name="$1"
  for _ in {1..90}; do
    local status
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$name" 2>/dev/null || true)"
    if [[ "$status" == "healthy" || "$status" == "running" ]]; then
      pass "${name} is ${status}"
      return 0
    fi
    sleep 2
  done
  echo "[ERROR] container health timeout: ${name}" >&2
  return 1
}

resolve_host_port() {
  local name="$1"
  local container_port="$2"
  local mapping

  mapping="$(docker port "$name" "$container_port" 2>/dev/null | head -n1 || true)"
  if [[ -z "$mapping" ]]; then
    echo "[ERROR] could not resolve host port for ${name} ${container_port}" >&2
    exit 1
  fi

  echo "$mapping" | sed -E 's/.*:([0-9]+)$/\1/'
}

wait_tcp_port() {
  local host="$1"
  local port="$2"

  for _ in {1..60}; do
    if (echo >"/dev/tcp/${host}/${port}") >/dev/null 2>&1; then
      pass "${host}:${port} is reachable"
      return 0
    fi
    sleep 1
  done

  echo "[ERROR] tcp port not reachable: ${host}:${port}" >&2
  return 1
}

wait_http_status() {
  local name="$1"
  local port="$2"
  local path="$3"
  local expected_status="$4"

  for _ in {1..180}; do
    local status
    status="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 2 "http://127.0.0.1:${port}${path}" || true)"
    if [[ "$status" == "$expected_status" ]]; then
      pass "${name} ready on ${path}"
      return 0
    fi
    sleep 2
  done

  echo "[ERROR] ${name} readiness timeout (port=${port}, path=${path}, expected=${expected_status})" >&2
  return 1
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
    sleep 2
  done
  echo "[ERROR] ${name} health timeout (port=${port})" >&2
  return 1
}

wait_dynamodb_local() {
  for _ in {1..60}; do
    local status
    status="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 2 "http://127.0.0.1:${DYNAMODB_PORT}" || true)"
    if [[ "$status" == "400" || "$status" == "200" ]]; then
      pass "dynamodb local is reachable"
      return 0
    fi
    sleep 1
  done
  echo "[ERROR] dynamodb local not reachable on port ${DYNAMODB_PORT}" >&2
  return 1
}

wait_dynamodb_bootstrap() {
  for _ in {1..60}; do
    local logs
    logs="$(cd "$ROOT/docker" && docker compose logs --no-color dynamodb-init 2>/dev/null || true)"
    if echo "$logs" | rg -q "DynamoDB table is ready|DynamoDB table already exists"; then
      pass "dynamodb cart table bootstrap complete"
      return 0
    fi
    sleep 1
  done
  echo "[ERROR] dynamodb table bootstrap timeout (${DYNAMODB_TABLE_NAME})" >&2
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
    echo "[ERROR] ${label} (expected=${expected}, actual=${actual})" >&2
    exit 1
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
    local code message
    code="$(echo "$body" | jq -r '.error.code // "UNKNOWN"')"
    message="$(echo "$body" | jq -r '.error.message // ""')"
    echo "[ERROR] ${label} failed (code=${code}, message=${message})" >&2
    exit 1
  fi
}

psql_exec() {
  local db="$1"
  local sql="$2"
  docker exec -i "$POSTGRES_CONTAINER" psql -U postgres -d "$db" -v ON_ERROR_STOP=1 -c "$sql" >/dev/null
}

psql_admin_query() {
  local sql="$1"
  docker exec -i "$POSTGRES_CONTAINER" psql -U postgres -d postgres -tAc "$sql"
}

psql_admin_exec() {
  local sql="$1"
  docker exec -i "$POSTGRES_CONTAINER" psql -U postgres -d postgres -v ON_ERROR_STOP=1 -c "$sql" >/dev/null
}

ensure_database() {
  local db="$1"
  local exists

  exists="$(psql_admin_query "SELECT 1 FROM pg_database WHERE datname = '${db}'")"
  if [[ "$exists" != "1" ]]; then
    psql_admin_exec "CREATE DATABASE ${db}"
    pass "database ${db} created"
  else
    pass "database ${db} already exists"
  fi

  psql_exec "$db" 'CREATE EXTENSION IF NOT EXISTS "uuid-ossp"'
  pass "database ${db} extension ready"
}

reset_database_schema() {
  local db="$1"
  psql_exec "$db" 'DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public; CREATE EXTENSION IF NOT EXISTS "uuid-ossp";'
  pass "database ${db} schema reset"
}

resolve_boot_jar() {
  local module_dir="$1"
  find "$module_dir/build/libs" -maxdepth 1 -type f -name '*.jar' ! -name '*plain*.jar' | sort | tail -n1
}

start_service() {
  local name="$1"
  local port="$2"
  local module_dir="$3"
  shift 3
  local log_file="$LOG_DIR/${name}.log"
  local jar_path
  local pid

  jar_path="$(resolve_boot_jar "$module_dir")"
  if [[ -z "$jar_path" ]]; then
    echo "[ERROR] boot jar not found for ${name}" >&2
    exit 1
  fi

  (
    cd "$ROOT"
    env "$@" java -jar "$jar_path" >"$log_file" 2>&1
  ) &
  pid="$!"
  PIDS+=("$pid")
  echo "[INFO] started ${name} on port ${port} (pid=${pid})"
}

urlencode() {
  local value="$1"
  jq -rn --arg v "$value" '$v|@uri'
}

wait_for_store_query_search() {
  local query="$1"
  local jq_expr="$2"
  local label="$3"
  local encoded_query raw body status

  encoded_query="$(urlencode "$query")"
  for _ in {1..120}; do
    raw="$(curl -sS -w '\n%{http_code}' \
      "http://127.0.0.1:${STORE_QUERY_PORT}/api/v1/store-query/stores?q=${encoded_query}&size=20" || true)"
    body="$(extract_http_body "$raw")"
    status="$(extract_http_status "$raw")"
    if [[ "$status" == "200" ]] \
      && [[ "$(echo "$body" | jq -r '.success // false')" == "true" ]] \
      && echo "$body" | jq -e "$jq_expr" >/dev/null; then
      pass "$label"
      return 0
    fi
    sleep 2
  done

  echo "[ERROR] timed out waiting for ${label}" >&2
  return 1
}

wait_for_store_query_detail() {
  local store_id="$1"
  local jq_expr="$2"
  local label="$3"
  local raw body status

  for _ in {1..120}; do
    raw="$(curl -sS -w '\n%{http_code}' \
      "http://127.0.0.1:${STORE_QUERY_PORT}/api/v1/store-query/stores/${store_id}" || true)"
    body="$(extract_http_body "$raw")"
    status="$(extract_http_status "$raw")"
    if [[ "$status" == "200" ]] \
      && [[ "$(echo "$body" | jq -r '.success // false')" == "true" ]] \
      && echo "$body" | jq -e "$jq_expr" >/dev/null; then
      pass "$label"
      return 0
    fi
    sleep 2
  done

  echo "[ERROR] timed out waiting for ${label}" >&2
  return 1
}

wait_for_stock_summary() {
  local item_id="$1"
  local reference_id="$2"
  local stock_item_type="$3"
  local expected_total="$4"
  local label="$5"
  local raw body status

  for _ in {1..120}; do
    raw="$(curl -sS -w '\n%{http_code}' \
      "http://127.0.0.1:${STOCK_PORT}/internal/v1/stock/items/${item_id}" || true)"
    body="$(extract_http_body "$raw")"
    status="$(extract_http_status "$raw")"
    if [[ "$status" == "200" ]] \
      && [[ "$(echo "$body" | jq -r '.success // false')" == "true" ]] \
      && echo "$body" | jq -e \
        --argjson ref "$reference_id" \
        --arg type "$stock_item_type" \
        --argjson total "$expected_total" \
        '.data.stocks[]? | select(.referenceId == $ref and .stockItemType == $type and .totalQuantity == $total and .availableQuantity == $total and .reservedQuantity == 0)' >/dev/null; then
      pass "$label" >&2
      echo "$body" | jq -r --argjson ref "$reference_id" --arg type "$stock_item_type" \
        '.data.stocks[] | select(.referenceId == $ref and .stockItemType == $type) | .stockItemId'
      return 0
    fi
    sleep 2
  done

  echo "[ERROR] timed out waiting for ${label}" >&2
  return 1
}

prepare_infra() {
  require_cmd docker
  require_cmd curl
  require_cmd jq
  require_cmd lsof
  require_cmd java
  require_cmd rg

  check_port_free "$PROFILE_PORT"
  check_port_free "$STORE_PORT"
  check_port_free "$PRODUCT_PORT"
  check_port_free "$STOCK_PORT"
  check_port_free "$SALES_PORT"
  check_port_free "$ORDER_PORT"
  check_port_free "$STORE_QUERY_PORT"
  check_port_free "$CART_PORT"

  echo "[INFO] starting docker infra"
  (
    cd "$ROOT/docker"
    docker compose up -d postgres redis zookeeper kafka dynamodb dynamodb-init >/dev/null
  )

  wait_container_health "$POSTGRES_CONTAINER"
  wait_container_health "project03-redis"
  wait_container_health "project03-zookeeper"
  wait_container_health "project03-kafka"
  wait_container_health "project03-dynamodb"
  wait_dynamodb_local
  wait_dynamodb_bootstrap

  POSTGRES_PORT="$(resolve_host_port "$POSTGRES_CONTAINER" "5432/tcp")"
  pass "postgres host port resolved to ${POSTGRES_PORT}"
  wait_tcp_port "127.0.0.1" "$POSTGRES_PORT"

  ensure_database "profile_db"
  ensure_database "store_db"
  ensure_database "store_query_db"
  ensure_database "product_db"
  ensure_database "stock_db"
  ensure_database "sales_db"
  ensure_database "order_db"
}

reset_databases() {
  echo "[INFO] resetting database schemas"
  reset_database_schema "profile_db"
  reset_database_schema "store_db"
  reset_database_schema "store_query_db"
  reset_database_schema "product_db"
  reset_database_schema "stock_db"
  reset_database_schema "sales_db"
  reset_database_schema "order_db"
}

build_services() {
  echo "[INFO] building boot jars"
  (
    cd "$ROOT"
    env \
      JAVA_HOME="${JAVA_HOME:-}" \
      PATH="$PATH" \
      GRADLE_USER_HOME="$GRADLE_USER_HOME" \
      ./gradlew \
      :servers:services:profile:bootJar \
      :servers:services:store:bootJar \
      :servers:services:product:bootJar \
      :servers:services:stock:bootJar \
      :servers:services:sales:bootJar \
      :servers:services:order:bootJar \
      :servers:services:store-query:bootJar \
      :servers:services:cart:bootJar
  ) >"$LOG_DIR/bootjar.log" 2>&1
  pass "boot jars built"
}

start_services() {
  echo "[INFO] starting profile/store/product/stock/order/sales/store-query/cart services"
  pass "using unique Kafka consumer groups with auto-offset-reset=latest"

  start_service "profile" "$PROFILE_PORT" "$ROOT/servers/services/profile" \
    SERVER_PORT="$PROFILE_PORT" \
    SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_PORT}/profile_db" \
    SPRING_DATASOURCE_USERNAME=postgres \
    SPRING_DATASOURCE_PASSWORD=postgres \
    REDIS_HOST=127.0.0.1 \
    REDIS_PORT="$REDIS_PORT" \
    KAFKA_BOOTSTRAP_SERVERS="127.0.0.1:${KAFKA_PORT}" \
    SPRING_KAFKA_CONSUMER_GROUP_ID="$PROFILE_CONSUMER_GROUP" \
    SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET=latest \
    APP_GATEWAY_SECURITY_ENABLED=true \
    APP_GATEWAY_SECURITY_GATEWAY_AUTH_ENABLED=false \
    APP_SECURITY_CRYPTO_ENCRYPTION_KEY=1234567890abcdef \
    JPA_SHOW_SQL=false

  start_service "store" "$STORE_PORT" "$ROOT/servers/services/store" \
    SERVER_PORT="$STORE_PORT" \
    SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_PORT}/store_db" \
    SPRING_DATASOURCE_USERNAME=postgres \
    SPRING_DATASOURCE_PASSWORD=postgres \
    REDIS_HOST=127.0.0.1 \
    REDIS_PORT="$REDIS_PORT" \
    KAFKA_BOOTSTRAP_SERVERS="127.0.0.1:${KAFKA_PORT}" \
    SPRING_KAFKA_CONSUMER_GROUP_ID="$STORE_CONSUMER_GROUP" \
    SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET=latest \
    JPA_SHOW_SQL=false

  start_service "product" "$PRODUCT_PORT" "$ROOT/servers/services/product" \
    SERVER_PORT="$PRODUCT_PORT" \
    SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_PORT}/product_db" \
    SPRING_DATASOURCE_USERNAME=postgres \
    SPRING_DATASOURCE_PASSWORD=postgres \
    REDIS_HOST=127.0.0.1 \
    REDIS_PORT="$REDIS_PORT" \
    KAFKA_BOOTSTRAP_SERVERS="127.0.0.1:${KAFKA_PORT}" \
    SPRING_KAFKA_CONSUMER_GROUP_ID="$PRODUCT_CONSUMER_GROUP" \
    SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET=latest \
    STORE_SERVICE_URL="http://127.0.0.1:${STORE_PORT}" \
    APP_GATEWAY_SECURITY_ENABLED=true \
    APP_GATEWAY_SECURITY_GATEWAY_AUTH_ENABLED=false \
    JPA_SHOW_SQL=false

  start_service "stock" "$STOCK_PORT" "$ROOT/servers/services/stock" \
    SERVER_PORT="$STOCK_PORT" \
    SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_PORT}/stock_db" \
    SPRING_DATASOURCE_USERNAME=postgres \
    SPRING_DATASOURCE_PASSWORD=postgres \
    REDIS_HOST=127.0.0.1 \
    REDIS_PORT="$REDIS_PORT" \
    KAFKA_BOOTSTRAP_SERVERS="127.0.0.1:${KAFKA_PORT}" \
    SPRING_KAFKA_CONSUMER_GROUP_ID="$STOCK_CONSUMER_GROUP" \
    SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET=latest \
    APP_GATEWAY_SECURITY_ENABLED=true \
    APP_GATEWAY_SECURITY_GATEWAY_AUTH_ENABLED=false \
    JPA_SHOW_SQL=false

  start_service "order" "$ORDER_PORT" "$ROOT/servers/services/order" \
    SERVER_PORT="$ORDER_PORT" \
    SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_PORT}/order_db" \
    SPRING_DATASOURCE_USERNAME=postgres \
    SPRING_DATASOURCE_PASSWORD=postgres \
    REDIS_HOST=127.0.0.1 \
    REDIS_PORT="$REDIS_PORT" \
    KAFKA_BOOTSTRAP_SERVERS="127.0.0.1:${KAFKA_PORT}" \
    SPRING_KAFKA_CONSUMER_GROUP_ID="$ORDER_CONSUMER_GROUP" \
    SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET=latest \
    JPA_SHOW_SQL=false

  start_service "sales" "$SALES_PORT" "$ROOT/servers/services/sales" \
    SERVER_PORT="$SALES_PORT" \
    SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_PORT}/sales_db" \
    SPRING_DATASOURCE_USERNAME=postgres \
    SPRING_DATASOURCE_PASSWORD=postgres \
    REDIS_HOST=127.0.0.1 \
    REDIS_PORT="$REDIS_PORT" \
    KAFKA_BOOTSTRAP_SERVERS="127.0.0.1:${KAFKA_PORT}" \
    SPRING_KAFKA_CONSUMER_GROUP_ID="$SALES_CONSUMER_GROUP" \
    SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET=latest \
    STOCK_SERVICE_URL="http://127.0.0.1:${STOCK_PORT}" \
    PRODUCT_SERVICE_URL="http://127.0.0.1:${PRODUCT_PORT}" \
    APP_SERVICE_ORDER_URL="http://127.0.0.1:${ORDER_PORT}" \
    APP_GATEWAY_SECURITY_ENABLED=true \
    APP_GATEWAY_SECURITY_GATEWAY_AUTH_ENABLED=false \
    JPA_SHOW_SQL=false

  start_service "store-query" "$STORE_QUERY_PORT" "$ROOT/servers/services/store-query" \
    SERVER_PORT="$STORE_QUERY_PORT" \
    SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_PORT}/store_query_db" \
    SPRING_DATASOURCE_USERNAME=postgres \
    SPRING_DATASOURCE_PASSWORD=postgres \
    REDIS_HOST=127.0.0.1 \
    REDIS_PORT="$REDIS_PORT" \
    KAFKA_BOOTSTRAP_SERVERS="127.0.0.1:${KAFKA_PORT}" \
    SPRING_KAFKA_CONSUMER_GROUP_ID="$STORE_QUERY_CONSUMER_GROUP" \
    SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET=latest \
    PROFILE_SERVICE_URL="http://127.0.0.1:${PROFILE_PORT}" \
    STORE_SERVICE_URL="http://127.0.0.1:${STORE_PORT}" \
    PRODUCT_SERVICE_URL="http://127.0.0.1:${PRODUCT_PORT}" \
    JPA_DDL_AUTO=none \
    APP_GATEWAY_SECURITY_ENABLED=true \
    APP_GATEWAY_SECURITY_GATEWAY_AUTH_ENABLED=false \
    JPA_SHOW_SQL=false

  start_service "cart" "$CART_PORT" "$ROOT/servers/services/cart" \
    SERVER_PORT="$CART_PORT" \
    SPRING_PROFILES_ACTIVE=local \
    CART_DYNAMODB_ENDPOINT="http://127.0.0.1:${DYNAMODB_PORT}" \
    CART_DYNAMODB_TABLE_NAME="$DYNAMODB_TABLE_NAME" \
    PRODUCT_SERVICE_URL="http://127.0.0.1:${PRODUCT_PORT}" \
    STORE_QUERY_SERVICE_URL="http://127.0.0.1:${STORE_QUERY_PORT}" \
    SALES_SERVICE_URL="http://127.0.0.1:${SALES_PORT}" \
    APP_GATEWAY_SECURITY_ENABLED=true \
    APP_GATEWAY_SECURITY_GATEWAY_AUTH_ENABLED=false

  wait_health "profile" "$PROFILE_PORT"
  wait_http_status "store" "$STORE_PORT" "/api/store/store_list" "200"
  wait_health "product" "$PRODUCT_PORT"
  wait_health "stock" "$STOCK_PORT"
  wait_health "order" "$ORDER_PORT"
  wait_health "sales" "$SALES_PORT"
  wait_health "store-query" "$STORE_QUERY_PORT"
  wait_health "cart" "$CART_PORT"

  echo "[INFO] waiting for Kafka consumer assignment"
  sleep 8
  pass "consumer warmup complete"
}

run_flow() {
  local raw body status
  local store_id store_2_id
  local goods_item_id goods_option_id goods_stock_item_id
  local perf_item_id perf_seat_grade_id perf_stock_item_id
  local order_id

  echo "[INFO] seeding seller profile projections"
  raw="$(curl -sS -w '\n%{http_code}' \
    -X POST "http://127.0.0.1:${PROFILE_PORT}/internal/v1/profiles" \
    -H 'Content-Type: application/json' \
    -d "{
      \"userId\": ${SELLER_ID},
      \"email\": \"${SELLER_EMAIL}\",
      \"nickname\": \"${OWNER_NICKNAME}\"
    }")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "seller profile internal create http status" "$status" "200"
  ensure_json_success "seller profile internal create success" "$body"

  raw="$(curl -sS -w '\n%{http_code}' \
    -X POST "http://127.0.0.1:${PROFILE_PORT}/internal/v1/profiles" \
    -H 'Content-Type: application/json' \
    -d "{
      \"userId\": ${SELLER_2_ID},
      \"email\": \"${SELLER_2_EMAIL}\",
      \"nickname\": \"${OWNER_2_NICKNAME}\"
    }")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "second seller profile internal create http status" "$status" "200"
  ensure_json_success "second seller profile internal create success" "$body"

  echo "[INFO] creating first store"
  raw="$(curl -sS -w '\n%{http_code}' \
    -X POST "http://127.0.0.1:${STORE_PORT}/api/store" \
    -H 'Content-Type: application/json' \
    -H "X-User-Id: ${SELLER_ID}" \
    -d "{
      \"storeName\": \"${STORE_NAME}\",
      \"address\": \"${STORE_ADDRESS}\",
      \"addressType\": \"MAIN\",
      \"contactValue\": \"${STORE_CONTACT}\",
      \"contactType\": \"PHONE\",
      \"description\": \"${STORE_DESCRIPTION}\",
      \"images\": []
    }")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "first store create http status" "$status" "200"
  ensure_json_success "first store create success" "$body"
  store_id="$(echo "$body" | jq -r '.data.id')"
  [[ -n "$store_id" && "$store_id" != "null" ]] || { echo "[ERROR] first store id missing" >&2; exit 1; }
  pass "first store created (${store_id})"

  echo "[INFO] creating second store"
  raw="$(curl -sS -w '\n%{http_code}' \
    -X POST "http://127.0.0.1:${STORE_PORT}/api/store" \
    -H 'Content-Type: application/json' \
    -H "X-User-Id: ${SELLER_2_ID}" \
    -d "{
      \"storeName\": \"${STORE_2_NAME}\",
      \"address\": \"${STORE_2_ADDRESS}\",
      \"addressType\": \"MAIN\",
      \"contactValue\": \"${STORE_2_CONTACT}\",
      \"contactType\": \"PHONE\",
      \"description\": \"${STORE_2_DESCRIPTION}\",
      \"images\": []
    }")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "second store create http status" "$status" "200"
  ensure_json_success "second store create success" "$body"
  store_2_id="$(echo "$body" | jq -r '.data.id')"
  [[ -n "$store_2_id" && "$store_2_id" != "null" ]] || { echo "[ERROR] second store id missing" >&2; exit 1; }
  pass "second store created (${store_2_id})"

  wait_for_store_query_search \
    "$STORE_NAME" \
    ".data.items[]? | select(.storeId == ${store_id} and .ownerNickname == \"${OWNER_NICKNAME}\")" \
    "store-query search reflects first store"
  wait_for_store_query_search \
    "$STORE_2_NAME" \
    ".data.items[]? | select(.storeId == ${store_2_id} and .ownerNickname == \"${OWNER_2_NICKNAME}\")" \
    "store-query search reflects second store"

  echo "[INFO] creating goods item in first store"
  raw="$(curl -sS -w '\n%{http_code}' \
    -X POST "http://127.0.0.1:${PRODUCT_PORT}/api/goods" \
    -H 'Content-Type: application/json' \
    -H "X-User-Id: ${SELLER_ID}" \
    -d "{
      \"title\": \"${GOODS_TITLE}\",
      \"description\": \"${GOODS_DESCRIPTION}\",
      \"price\": ${GOODS_PRICE},
      \"storeId\": ${store_id},
      \"options\": [
        {
          \"optionName\": \"${GOODS_OPTION_NAME}\",
          \"additionalPrice\": ${GOODS_OPTION_EXTRA_PRICE},
          \"stockQuantity\": ${GOODS_STOCK_TOTAL}
        }
      ],
      \"shippingInfo\": {
        \"shippingFee\": 0,
        \"freeShippingThreshold\": 0,
        \"estimatedDays\": 2,
        \"returnPolicy\": \"7 day exchange\"
      }
    }")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "goods create http status" "$status" "200"
  ensure_json_success "goods create success" "$body"
  goods_item_id="$(echo "$body" | jq -r '.data.id')"
  goods_option_id="$(echo "$body" | jq -r '.data.options[0].id')"
  [[ -n "$goods_item_id" && "$goods_item_id" != "null" ]] || { echo "[ERROR] goods item id missing" >&2; exit 1; }
  [[ -n "$goods_option_id" && "$goods_option_id" != "null" ]] || { echo "[ERROR] goods option id missing" >&2; exit 1; }
  pass "goods created itemId=${goods_item_id} optionId=${goods_option_id}"

  echo "[INFO] creating performance item in second store"
  raw="$(curl -sS -w '\n%{http_code}' \
    -X POST "http://127.0.0.1:${PRODUCT_PORT}/api/performances" \
    -H 'Content-Type: application/json' \
    -H "X-User-Id: ${SELLER_2_ID}" \
    -d "{
      \"title\": \"${PERFORMANCE_TITLE}\",
      \"description\": \"${PERFORMANCE_DESCRIPTION}\",
      \"price\": ${PERFORMANCE_GRADE_PRICE},
      \"storeId\": ${store_2_id},
      \"venue\": \"${PERFORMANCE_VENUE}\",
      \"performanceDate\": \"${PERFORMANCE_DATE}\",
      \"performanceTime\": \"${PERFORMANCE_TIME}\",
      \"totalSeats\": ${PERFORMANCE_TOTAL_SEATS},
      \"seatGrades\": [
        {
          \"gradeName\": \"${PERFORMANCE_GRADE_NAME}\",
          \"price\": ${PERFORMANCE_GRADE_PRICE},
          \"totalQuantity\": ${PERFORMANCE_GRADE_TOTAL},
          \"fundingQuantity\": ${PERFORMANCE_GRADE_FUNDING}
        }
      ],
      \"castMembers\": [
        {
          \"name\": \"Cart E2E Cast\",
          \"role\": \"Lead\",
          \"profileImageUrl\": \"https://example.com/cart-cast.jpg\"
        }
      ]
    }")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "performance create http status" "$status" "200"
  ensure_json_success "performance create success" "$body"
  perf_item_id="$(echo "$body" | jq -r '.data.id')"
  perf_seat_grade_id="$(echo "$body" | jq -r '.data.seatGrades[0].id')"
  [[ -n "$perf_item_id" && "$perf_item_id" != "null" ]] || { echo "[ERROR] performance item id missing" >&2; exit 1; }
  [[ -n "$perf_seat_grade_id" && "$perf_seat_grade_id" != "null" ]] || { echo "[ERROR] performance seat grade id missing" >&2; exit 1; }
  pass "performance created itemId=${perf_item_id} seatGradeId=${perf_seat_grade_id}"

  goods_stock_item_id="$(wait_for_stock_summary "$goods_item_id" "$goods_option_id" "ITEM_OPTION" "$GOODS_STOCK_TOTAL" "goods stock initialized")"
  perf_stock_item_id="$(wait_for_stock_summary "$perf_item_id" "$perf_seat_grade_id" "SEAT_GRADE" "$PERFORMANCE_GRADE_TOTAL" "performance seat stock initialized")"
  [[ -n "$goods_stock_item_id" && "$goods_stock_item_id" != "null" ]] || { echo "[ERROR] goods stock item id missing" >&2; exit 1; }
  [[ -n "$perf_stock_item_id" && "$perf_stock_item_id" != "null" ]] || { echo "[ERROR] performance stock item id missing" >&2; exit 1; }

  echo "[INFO] changing item statuses to ON_SALE"
  raw="$(curl -sS -w '\n%{http_code}' \
    -X PATCH "http://127.0.0.1:${PRODUCT_PORT}/api/items/${goods_item_id}/status" \
    -H 'Content-Type: application/json' \
    -H "X-User-Id: ${SELLER_ID}" \
    -d '{
      "status": "ON_SALE",
      "reason": "cart e2e goods"
    }')"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "goods status change http status" "$status" "200"
  ensure_json_success "goods status change success" "$body"

  raw="$(curl -sS -w '\n%{http_code}' \
    -X PATCH "http://127.0.0.1:${PRODUCT_PORT}/api/items/${perf_item_id}/status" \
    -H 'Content-Type: application/json' \
    -H "X-User-Id: ${SELLER_2_ID}" \
    -d '{
      "status": "ON_SALE",
      "reason": "cart e2e performance"
    }')"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "performance status change http status" "$status" "200"
  ensure_json_success "performance status change success" "$body"

  wait_for_store_query_detail \
    "$store_id" \
    ".data.ownerNickname == \"${OWNER_NICKNAME}\" and (.data.items[]? | select(.itemId == ${goods_item_id} and .title == \"${GOODS_TITLE}\" and .status == \"ON_SALE\"))" \
    "store-query detail reflects goods item"
  wait_for_store_query_detail \
    "$store_2_id" \
    ".data.ownerNickname == \"${OWNER_2_NICKNAME}\" and (.data.items[]? | select(.itemId == ${perf_item_id} and .title == \"${PERFORMANCE_TITLE}\" and .status == \"ON_SALE\"))" \
    "store-query detail reflects performance item"
  wait_for_store_query_search \
    "$GOODS_TITLE" \
    ".data.items[]? | select(.storeId == ${store_id})" \
    "store-query goods title search ready"
  wait_for_store_query_search \
    "$PERFORMANCE_TITLE" \
    ".data.items[]? | select(.storeId == ${store_2_id})" \
    "store-query performance title search ready"

  echo "[INFO] adding goods line to cart"
  raw="$(curl -sS -w '\n%{http_code}' \
    -X POST "http://127.0.0.1:${CART_PORT}/api/v1/cart/items" \
    -H 'Content-Type: application/json' \
    -H "X-User-Id: ${BUYER_ID}" \
    -d "{
      \"itemId\": ${goods_item_id},
      \"channelType\": \"NORMAL\",
      \"channelRefId\": null,
      \"stockItemType\": \"ITEM_OPTION\",
      \"referenceId\": ${goods_option_id},
      \"quantity\": 2,
      \"selected\": true
    }")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "cart add goods http status" "$status" "200"
  ensure_json_success "cart add goods success" "$body"

  echo "[INFO] adding performance line to cart (initially unselected)"
  raw="$(curl -sS -w '\n%{http_code}' \
    -X POST "http://127.0.0.1:${CART_PORT}/api/v1/cart/items" \
    -H 'Content-Type: application/json' \
    -H "X-User-Id: ${BUYER_ID}" \
    -d "{
      \"itemId\": ${perf_item_id},
      \"channelType\": \"NORMAL\",
      \"channelRefId\": null,
      \"stockItemType\": \"SEAT_GRADE\",
      \"referenceId\": ${perf_seat_grade_id},
      \"quantity\": 1,
      \"selected\": false
    }")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "cart add performance http status" "$status" "200"
  ensure_json_success "cart add performance success" "$body"

  echo "[INFO] verifying cart snapshot after initial add"
  raw="$(curl -sS -w '\n%{http_code}' \
    -H "X-User-Id: ${BUYER_ID}" \
    "http://127.0.0.1:${CART_PORT}/api/v1/cart")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "cart get after add http status" "$status" "200"
  ensure_json_success "cart get after add success" "$body"
  if echo "$body" | jq -e \
    --argjson goodsItemId "$goods_item_id" \
    --argjson goodsRefId "$goods_option_id" \
    --arg goodsTitle "$GOODS_TITLE" \
    --arg goodsStoreName "$STORE_NAME" \
    --argjson goodsStoreId "$store_id" \
    --argjson perfItemId "$perf_item_id" \
    --argjson perfRefId "$perf_seat_grade_id" \
    --arg perfTitle "$PERFORMANCE_TITLE" \
    --arg perfStoreName "$STORE_2_NAME" \
    --argjson perfStoreId "$store_2_id" \
    '.data.itemCount == 2
      and .data.selectedItemCount == 1
      and any(.data.items[]?; .itemId == $goodsItemId and .referenceId == $goodsRefId and .quantity == 2 and .selected == true and .stockItemType == "ITEM_OPTION" and .itemTitle == $goodsTitle and .storeId == $goodsStoreId and .storeName == $goodsStoreName)
      and any(.data.items[]?; .itemId == $perfItemId and .referenceId == $perfRefId and .quantity == 1 and .selected == false and .stockItemType == "SEAT_GRADE" and .itemTitle == $perfTitle and .storeId == $perfStoreId and .storeName == $perfStoreName)' >/dev/null; then
    pass "cart contains cross-store goods and performance lines"
  else
    echo "[ERROR] cart snapshot after add mismatch" >&2
    exit 1
  fi

  echo "[INFO] updating goods quantity"
  raw="$(curl -sS -w '\n%{http_code}' \
    -X PATCH "http://127.0.0.1:${CART_PORT}/api/v1/cart/items/quantity" \
    -H 'Content-Type: application/json' \
    -H "X-User-Id: ${BUYER_ID}" \
    -d "{
      \"itemId\": ${goods_item_id},
      \"referenceId\": ${goods_option_id},
      \"channelType\": \"NORMAL\",
      \"channelRefId\": null,
      \"quantity\": ${GOODS_FINAL_QUANTITY}
    }")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "cart update goods quantity http status" "$status" "200"
  ensure_json_success "cart update goods quantity success" "$body"

  echo "[INFO] adding same performance line again to trigger merge and selection refresh"
  raw="$(curl -sS -w '\n%{http_code}' \
    -X POST "http://127.0.0.1:${CART_PORT}/api/v1/cart/items" \
    -H 'Content-Type: application/json' \
    -H "X-User-Id: ${BUYER_ID}" \
    -d "{
      \"itemId\": ${perf_item_id},
      \"channelType\": \"NORMAL\",
      \"channelRefId\": null,
      \"stockItemType\": \"SEAT_GRADE\",
      \"referenceId\": ${perf_seat_grade_id},
      \"quantity\": 2,
      \"selected\": true
    }")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "cart merge performance line http status" "$status" "200"
  ensure_json_success "cart merge performance line success" "$body"

  raw="$(curl -sS -w '\n%{http_code}' \
    -H "X-User-Id: ${BUYER_ID}" \
    "http://127.0.0.1:${CART_PORT}/api/v1/cart")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "cart get after quantity changes http status" "$status" "200"
  ensure_json_success "cart get after quantity changes success" "$body"
  if echo "$body" | jq -e \
    --argjson goodsItemId "$goods_item_id" \
    --argjson goodsRefId "$goods_option_id" \
    --argjson perfItemId "$perf_item_id" \
    --argjson perfRefId "$perf_seat_grade_id" \
    --argjson goodsQty "$GOODS_FINAL_QUANTITY" \
    --argjson perfQty "$PERFORMANCE_FINAL_QUANTITY" \
    '.data.itemCount == 2
      and .data.selectedItemCount == 2
      and any(.data.items[]?; .itemId == $goodsItemId and .referenceId == $goodsRefId and .quantity == $goodsQty and .selected == true and .stockItemType == "ITEM_OPTION")
      and any(.data.items[]?; .itemId == $perfItemId and .referenceId == $perfRefId and .quantity == $perfQty and .selected == true and .stockItemType == "SEAT_GRADE")' >/dev/null; then
    pass "cart quantities and merged performance line updated"
  else
    echo "[ERROR] cart after quantity changes mismatch" >&2
    exit 1
  fi

  echo "[INFO] changing selections to single selected line"
  raw="$(curl -sS -w '\n%{http_code}' \
    -X PATCH "http://127.0.0.1:${CART_PORT}/api/v1/cart/items/selection" \
    -H 'Content-Type: application/json' \
    -H "X-User-Id: ${BUYER_ID}" \
    -d "{
      \"lineItems\": [
        {
          \"itemId\": ${goods_item_id},
          \"referenceId\": ${goods_option_id},
          \"channelType\": \"NORMAL\",
          \"channelRefId\": null,
          \"selected\": false
        },
        {
          \"itemId\": ${perf_item_id},
          \"referenceId\": ${perf_seat_grade_id},
          \"channelType\": \"NORMAL\",
          \"channelRefId\": null,
          \"selected\": true
        }
      ]
    }")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "cart selection change http status" "$status" "200"
  ensure_json_success "cart selection change success" "$body"
  if echo "$body" | jq -e '.data.itemCount == 2 and .data.selectedItemCount == 1' >/dev/null; then
    pass "cart selection count reflects single selected line"
  else
    echo "[ERROR] cart selection count mismatch after partial deselect" >&2
    exit 1
  fi

  echo "[INFO] re-selecting all cart lines"
  raw="$(curl -sS -w '\n%{http_code}' \
    -X PATCH "http://127.0.0.1:${CART_PORT}/api/v1/cart/items/selection" \
    -H 'Content-Type: application/json' \
    -H "X-User-Id: ${BUYER_ID}" \
    -d "{
      \"lineItems\": [
        {
          \"itemId\": ${goods_item_id},
          \"referenceId\": ${goods_option_id},
          \"channelType\": \"NORMAL\",
          \"channelRefId\": null,
          \"selected\": true
        },
        {
          \"itemId\": ${perf_item_id},
          \"referenceId\": ${perf_seat_grade_id},
          \"channelType\": \"NORMAL\",
          \"channelRefId\": null,
          \"selected\": true
        }
      ]
    }")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "cart reselection http status" "$status" "200"
  ensure_json_success "cart reselection success" "$body"
  if echo "$body" | jq -e '.data.itemCount == 2 and .data.selectedItemCount == 2' >/dev/null; then
    pass "cart selection count reflects all selected lines"
  else
    echo "[ERROR] cart selection count mismatch after reselection" >&2
    exit 1
  fi

  echo "[INFO] starting checkout from selected cart lines"
  raw="$(curl -sS -w '\n%{http_code}' \
    -X POST "http://127.0.0.1:${CART_PORT}/api/v1/cart/checkout/reservations" \
    -H 'Content-Type: application/json' \
    -H "X-User-Id: ${BUYER_ID}" \
    -d "{
      \"idempotencyKey\": \"${IDEMPOTENCY_KEY}\"
    }")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "cart checkout reserve http status" "$status" "200"
  ensure_json_success "cart checkout reserve success" "$body"
  order_id="$(echo "$body" | jq -r '.data.orderId')"
  [[ -n "$order_id" && "$order_id" != "null" ]] || { echo "[ERROR] cart checkout orderId missing" >&2; exit 1; }
  pass "cart checkout returned orderId=${order_id}"

  if echo "$body" | jq -e \
    --argjson goodsItemId "$goods_item_id" \
    --argjson goodsRefId "$goods_option_id" \
    --argjson perfItemId "$perf_item_id" \
    --argjson perfRefId "$perf_seat_grade_id" \
    --argjson goodsQty "$GOODS_FINAL_QUANTITY" \
    --argjson perfQty "$PERFORMANCE_FINAL_QUANTITY" \
    'any(.data.reservedItems[]?; .itemId == $goodsItemId and .referenceId == $goodsRefId and .stockItemType == "ITEM_OPTION" and .quantity == $goodsQty)
      and any(.data.reservedItems[]?; .itemId == $perfItemId and .referenceId == $perfRefId and .stockItemType == "SEAT_GRADE" and .quantity == $perfQty)' >/dev/null; then
    pass "cart checkout reserve preserved mixed stock item types"
  else
    echo "[ERROR] cart checkout reserved items mismatch" >&2
    exit 1
  fi

  raw="$(curl -sS -w '\n%{http_code}' \
    "http://127.0.0.1:${STOCK_PORT}/internal/v1/stock/reservations?orderId=${order_id}")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "stock reservation lookup http status" "$status" "200"
  ensure_json_success "stock reservation lookup success" "$body"
  if echo "$body" | jq -e \
    --arg goodsStockItemId "$goods_stock_item_id" \
    --arg perfStockItemId "$perf_stock_item_id" \
    --argjson goodsQty "$GOODS_FINAL_QUANTITY" \
    --argjson perfQty "$PERFORMANCE_FINAL_QUANTITY" \
    'any(.data[]?; (.stockItemId | tostring) == $goodsStockItemId and .status == "RESERVED" and .quantity == $goodsQty)
      and any(.data[]?; (.stockItemId | tostring) == $perfStockItemId and .status == "RESERVED" and .quantity == $perfQty)' >/dev/null; then
    pass "stock reservations locked cart checkout quantities"
  else
    echo "[ERROR] stock reservations mismatch after cart checkout" >&2
    exit 1
  fi

  raw="$(curl -sS -w '\n%{http_code}' \
    -X POST "http://127.0.0.1:${SALES_PORT}/api/v1/sales/checkout/quotes" \
    -H 'Content-Type: application/json' \
    -H "X-User-Id: ${BUYER_ID}" \
    -d "{
      \"orderId\": ${order_id}
    }")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "checkout quote http status" "$status" "200"
  ensure_json_success "checkout quote success" "$body"
  if echo "$body" | jq -e \
    --argjson expectedTotal "$EXPECTED_TOTAL" \
    --arg goodsItemId "$goods_item_id" \
    --arg goodsRefId "$goods_option_id" \
    --argjson goodsQty "$GOODS_FINAL_QUANTITY" \
    --argjson goodsAmount "$GOODS_LINE_AMOUNT" \
    --arg perfItemId "$perf_item_id" \
    --arg perfRefId "$perf_seat_grade_id" \
    --argjson perfQty "$PERFORMANCE_FINAL_QUANTITY" \
    --argjson perfAmount "$PERFORMANCE_LINE_AMOUNT" \
    '.data.totalAmount == $expectedTotal
      and any(.data.lineItems[]?; (.itemId | tostring) == $goodsItemId and (.referenceId | tostring) == $goodsRefId and .stockItemType == "ITEM_OPTION" and .quantity == $goodsQty and .lineAmount == $goodsAmount)
      and any(.data.lineItems[]?; (.itemId | tostring) == $perfItemId and (.referenceId | tostring) == $perfRefId and .stockItemType == "SEAT_GRADE" and .quantity == $perfQty and .lineAmount == $perfAmount)' >/dev/null; then
    pass "checkout quote preserves cross-store mixed-type totals"
  else
    echo "[ERROR] checkout quote mismatch after cart reserve" >&2
    exit 1
  fi
}

main() {
  prepare_infra
  reset_databases
  build_services
  start_services
  run_flow

  echo
  echo "[SUMMARY] passes=${PASSES} failures=${FAILURES}"
  if [[ "$FAILURES" -ne 0 ]]; then
    exit 1
  fi
}

main "$@"
