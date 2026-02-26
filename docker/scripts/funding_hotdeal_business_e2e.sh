#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOG_DIR="${LOG_DIR:-/tmp/funding_hotdeal_business_e2e_logs_$(date +%Y%m%d_%H%M%S)}"
mkdir -p "$LOG_DIR"

PRODUCT_PORT="${PRODUCT_PORT:-18284}"
STOCK_PORT="${STOCK_PORT:-18285}"
FUNDING_PORT="${FUNDING_PORT:-18286}"
HOTDEAL_PORT="${HOTDEAL_PORT:-18289}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-project03-kafka}"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/.gradle-codex-e2e}"

NOW_TS="$(date +%s)"
SELLER_ID="${SELLER_ID:-610001}"
BUYER_ID="${BUYER_ID:-620001}"
BUYER2_ID="${BUYER2_ID:-620002}"
BUYER3_ID="${BUYER3_ID:-620003}"
STORE_ID="${STORE_ID:-630001}"
CONSUMER_GROUP_SUFFIX="${CONSUMER_GROUP_SUFFIX:-$NOW_TS}"
STOCK_CONSUMER_GROUP="${STOCK_CONSUMER_GROUP:-stock-business-e2e-${CONSUMER_GROUP_SUFFIX}}"
PRODUCT_CONSUMER_GROUP="${PRODUCT_CONSUMER_GROUP:-product-business-e2e-${CONSUMER_GROUP_SUFFIX}}"
FUNDING_CONSUMER_GROUP="${FUNDING_CONSUMER_GROUP:-funding-business-e2e-${CONSUMER_GROUP_SUFFIX}}"
HOTDEAL_CONSUMER_GROUP="${HOTDEAL_CONSUMER_GROUP:-hotdeal-business-e2e-${CONSUMER_GROUP_SUFFIX}}"

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

ensure_error_code() {
  local label="$1"
  local body="$2"
  local expected="$3"
  local actual
  actual="$(echo "$body" | jq -r '.error.code // empty')"
  if [[ "$actual" == "$expected" ]]; then
    pass "$label"
  else
    fail "$label (expected=${expected}, actual=${actual})"
  fi
}

format_local_from_epoch() {
  local epoch="$1"
  date -r "$epoch" '+%Y-%m-%dT%H:%M:%S' 2>/dev/null || date -d "@${epoch}" '+%Y-%m-%dT%H:%M:%S'
}

wait_stock_item_id_by_reference() {
  local item_id="$1"
  local reference_id="$2"
  for _ in {1..120}; do
    local raw body status stock_item_id
    raw="$(curl -sS -w '\n%{http_code}' \
      "http://127.0.0.1:${STOCK_PORT}/internal/v1/stock/items/${item_id}" || true)"
    body="$(extract_http_body "$raw")"
    status="$(extract_http_status "$raw")"
    if [[ "$status" == "200" ]] && [[ "$(echo "$body" | jq -r '.success // false')" == "true" ]]; then
      stock_item_id="$(echo "$body" | jq -r --arg ref "$reference_id" '.data.stocks[]? | select((.referenceId|tostring) == $ref) | .stockItemId // empty' | head -n1)"
      if [[ -n "$stock_item_id" && "$stock_item_id" != "null" ]]; then
        echo "$stock_item_id"
        return 0
      fi
    fi
    sleep 1
  done
  return 1
}

wait_participation_status() {
  local campaign_id="$1"
  local participation_id="$2"
  local expected_status="$3"
  local timeout_seconds="${4:-60}"

  for _ in $(seq 1 "$timeout_seconds"); do
    local raw body status value
    raw="$(curl -sS -w '\n%{http_code}' \
      "http://127.0.0.1:${FUNDING_PORT}/api/campaigns/${campaign_id}/participations" || true)"
    body="$(extract_http_body "$raw")"
    status="$(extract_http_status "$raw")"
    if [[ "$status" == "200" ]] && [[ "$(echo "$body" | jq -r '.success // false')" == "true" ]]; then
      value="$(echo "$body" | jq -r --arg id "$participation_id" '.data[]? | select((.id|tostring) == $id) | .status // empty' | head -n1)"
      if [[ "$value" == "$expected_status" ]]; then
        pass "participation status became ${expected_status}"
        return 0
      fi
    fi
    sleep 1
  done
  fail "participation status ${expected_status} wait timeout"
  return 1
}

wait_funding_progress_quantity() {
  local campaign_id="$1"
  local expected_quantity="$2"
  local timeout_seconds="${3:-60}"

  for _ in $(seq 1 "$timeout_seconds"); do
    local raw body status quantity
    raw="$(curl -sS -w '\n%{http_code}' \
      "http://127.0.0.1:${FUNDING_PORT}/api/campaigns/${campaign_id}/progress" || true)"
    body="$(extract_http_body "$raw")"
    status="$(extract_http_status "$raw")"
    if [[ "$status" == "200" ]] && [[ "$(echo "$body" | jq -r '.success // false')" == "true" ]]; then
      quantity="$(echo "$body" | jq -r '.data.currentQuantity // -1')"
      if [[ "$quantity" == "$expected_quantity" ]]; then
        pass "funding currentQuantity is ${expected_quantity}"
        return 0
      fi
    fi
    sleep 1
  done
  fail "funding currentQuantity ${expected_quantity} wait timeout"
  return 1
}

wait_queue_admitted() {
  local hotdeal_id="$1"
  local user_id="$2"
  local timeout_seconds="${3:-60}"

  for _ in $(seq 1 "$timeout_seconds"); do
    local raw body status can_purchase
    raw="$(curl -sS -w '\n%{http_code}' \
      "http://127.0.0.1:${HOTDEAL_PORT}/api/v1/hot-deals/${hotdeal_id}/queue" \
      -H "X-User-Id: ${user_id}" || true)"
    body="$(extract_http_body "$raw")"
    status="$(extract_http_status "$raw")"
    if [[ "$status" == "200" ]] && [[ "$(echo "$body" | jq -r '.success // false')" == "true" ]]; then
      can_purchase="$(echo "$body" | jq -r '.data.canPurchase // false')"
      if [[ "$can_purchase" == "true" ]]; then
        pass "queue admitted (hotDealId=${hotdeal_id}, userId=${user_id})"
        return 0
      fi
    fi
    sleep 1
  done
  fail "queue admission timeout (hotDealId=${hotdeal_id}, userId=${user_id})"
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
      APP_GATEWAY_SECURITY_ENABLED=false \
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
      APP_GATEWAY_SECURITY_ENABLED=false \
      ./gradlew :servers:services:product:bootRun --no-daemon >"$LOG_DIR/product.log" 2>&1
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
      APP_GATEWAY_SECURITY_ENABLED=false \
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
      APP_GATEWAY_SECURITY_ENABLED=false \
      ./gradlew :servers:services:hot-deal:bootRun --no-daemon >"$LOG_DIR/hotdeal.log" 2>&1
  ) &
  PIDS+=("$!")
}

run_funding_business_flow() {
  local ts keyword product_payload create_raw create_body create_status
  local funding_item_id funding_option_id status_raw status_body status_code
  local start_at end_at campaign_payload campaign_raw campaign_body campaign_status campaign_id
  local participate_raw participate_body participate_status participation_id reservation_id
  local me_raw me_body me_status contains_me
  local refund_raw refund_body refund_status

  echo "[INFO] funding business flow"

  ts="$(date +%s)"
  keyword="funding-business-${ts}"

  product_payload="$(cat <<JSON
{"title":"Funding Product ${keyword}","description":"funding business e2e","price":12000,"storeId":${STORE_ID},"options":[{"optionName":"기본","additionalPrice":0,"stockQuantity":5}],"shippingInfo":{"shippingFee":2500,"freeShippingThreshold":40000,"estimatedDays":2,"returnPolicy":"returnable"}}
JSON
)"

  create_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${PRODUCT_PORT}/api/products" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: ${SELLER_ID}" \
    -d "$product_payload")"
  create_body="$(extract_http_body "$create_raw")"
  create_status="$(extract_http_status "$create_raw")"
  ensure_http_status "funding product create status" "$create_status" "200" "$create_body" || return 1
  ensure_json_success "funding product create success" "$create_body" || return 1

  funding_item_id="$(echo "$create_body" | jq -r '.data.id // empty')"
  funding_option_id="$(echo "$create_body" | jq -r '.data.options[0].id // empty')"
  if [[ -z "$funding_item_id" || "$funding_item_id" == "null" ]]; then
    fail "funding item id resolved"
    return 1
  fi
  if [[ -z "$funding_option_id" || "$funding_option_id" == "null" ]]; then
    fail "funding option id resolved"
    return 1
  fi
  pass "funding item/option resolved (itemId=${funding_item_id}, optionId=${funding_option_id})"

  status_raw="$(curl -sS -w '\n%{http_code}' -X PATCH "http://127.0.0.1:${PRODUCT_PORT}/api/items/${funding_item_id}/status" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: ${SELLER_ID}" \
    -d '{"status":"FUNDING","reason":"funding business e2e"}')"
  status_body="$(extract_http_body "$status_raw")"
  status_code="$(extract_http_status "$status_raw")"
  ensure_http_status "item status DRAFT->FUNDING status" "$status_code" "200" "$status_body" || return 1
  ensure_json_success "item status DRAFT->FUNDING success" "$status_body" || return 1

  if wait_stock_item_id_by_reference "$funding_item_id" "$funding_option_id" >/dev/null; then
    pass "stock initialized for funding item option"
  else
    fail "stock initialization for funding item option"
    return 1
  fi

  start_at="$(format_local_from_epoch $((ts - 60)))"
  end_at="$(format_local_from_epoch $((ts + 1800)))"
  campaign_payload="$(cat <<JSON
{"itemId":${funding_item_id},"title":"Funding Campaign ${keyword}","summary":"quantity based campaign","makerName":"MZC","category":"TEST","fundingType":"QUANTITY_BASED","goalAmount":20000,"goalQuantity":2,"minAmount":1000,"startAt":"${start_at}","endAt":"${end_at}"}
JSON
)"

  campaign_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${FUNDING_PORT}/api/campaigns" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: ${SELLER_ID}" \
    -d "$campaign_payload")"
  campaign_body="$(extract_http_body "$campaign_raw")"
  campaign_status="$(extract_http_status "$campaign_raw")"
  ensure_http_status "campaign create status" "$campaign_status" "200" "$campaign_body" || return 1
  ensure_json_success "campaign create success" "$campaign_body" || return 1

  campaign_id="$(echo "$campaign_body" | jq -r '.data.id // empty')"
  if [[ -z "$campaign_id" || "$campaign_id" == "null" ]]; then
    fail "campaign id resolved"
    return 1
  fi
  pass "campaign id resolved (${campaign_id})"

  ensure_http_status "campaign detail status" \
    "$(curl -sS -o "$LOG_DIR/campaign_detail.json" -w '%{http_code}' "http://127.0.0.1:${FUNDING_PORT}/api/campaigns/${campaign_id}")" \
    "200" || return 1
  ensure_jq_true_campaign_detail="$(jq -r '.success // false' "$LOG_DIR/campaign_detail.json" 2>/dev/null || true)"
  if [[ "$ensure_jq_true_campaign_detail" == "true" ]]; then
    pass "campaign detail success payload"
  else
    fail "campaign detail success payload"
    return 1
  fi

  ensure_http_status "campaign by item status" \
    "$(curl -sS -o "$LOG_DIR/campaign_by_item.json" -w '%{http_code}' "http://127.0.0.1:${FUNDING_PORT}/api/campaigns/item/${funding_item_id}")" \
    "200" || return 1
  ensure_jq_true_campaign_item="$(jq -r '.success // false' "$LOG_DIR/campaign_by_item.json" 2>/dev/null || true)"
  if [[ "$ensure_jq_true_campaign_item" == "true" ]]; then
    pass "campaign by item success payload"
  else
    fail "campaign by item success payload"
    return 1
  fi

  participate_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${FUNDING_PORT}/api/campaigns/${campaign_id}/participate" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: ${BUYER_ID}" \
    -d "{\"amount\":12000,\"quantity\":1,\"itemOptionId\":${funding_option_id}}")"
  participate_body="$(extract_http_body "$participate_raw")"
  participate_status="$(extract_http_status "$participate_raw")"
  ensure_http_status "funding participate status" "$participate_status" "200" "$participate_body" || return 1
  ensure_json_success "funding participate success" "$participate_body" || return 1

  participation_id="$(echo "$participate_body" | jq -r '.data.id // empty')"
  reservation_id="$(echo "$participate_body" | jq -r '.data.reservationId // empty')"
  if [[ -z "$participation_id" || "$participation_id" == "null" ]]; then
    fail "participation id resolved"
    return 1
  fi
  if [[ -z "$reservation_id" || "$reservation_id" == "null" ]]; then
    fail "reservation id resolved for quantity-based participation"
    return 1
  fi
  pass "participation/reservation resolved (${participation_id}/${reservation_id})"

  wait_funding_progress_quantity "$campaign_id" "1" 60 || return 1

  me_raw="$(curl -sS -w '\n%{http_code}' "http://127.0.0.1:${FUNDING_PORT}/api/campaigns/participations/me?size=20" \
    -H "X-User-Id: ${BUYER_ID}")"
  me_body="$(extract_http_body "$me_raw")"
  me_status="$(extract_http_status "$me_raw")"
  ensure_http_status "my participations status" "$me_status" "200" "$me_body" || return 1
  ensure_json_success "my participations success" "$me_body" || return 1
  contains_me="$(echo "$me_body" | jq -r --arg id "$participation_id" '((.data.items // .data.content // []) | map(.id|tostring) | index($id)) != null')"
  if [[ "$contains_me" == "true" ]]; then
    pass "my participations includes created participation"
  else
    fail "my participations includes created participation"
    return 1
  fi

  refund_raw="$(curl -sS -w '\n%{http_code}' -X POST \
    "http://127.0.0.1:${FUNDING_PORT}/internal/v1/funding/participations/${participation_id}/refund?userId=${BUYER_ID}")"
  refund_body="$(extract_http_body "$refund_raw")"
  refund_status="$(extract_http_status "$refund_raw")"
  ensure_http_status "participation refund status" "$refund_status" "200" "$refund_body" || return 1
  ensure_json_success "participation refund success" "$refund_body" || return 1

  wait_participation_status "$campaign_id" "$participation_id" "REFUNDED" 60 || return 1
  wait_funding_progress_quantity "$campaign_id" "0" 60 || return 1
}

run_hotdeal_business_flow() {
  local ts keyword product_payload create_raw create_body create_status
  local hotdeal_item_id status_raw status_body status_code
  local hotdeal_payload hotdeal_raw hotdeal_body hotdeal_status hotdeal_id
  local queue_raw queue_body queue_status
  local purchase_raw purchase_body purchase_status
  local repurchase_raw repurchase_body repurchase_status
  local purchase2_raw purchase2_body purchase2_status
  local soldout_raw soldout_body soldout_status
  local list_raw list_body list_status contains_hotdeal
  local detail_raw detail_body detail_status

  echo "[INFO] hot-deal business flow"

  ts="$(date +%s)"
  keyword="hotdeal-business-${ts}"
  product_payload="$(cat <<JSON
{"title":"HotDeal Product ${keyword}","description":"hotdeal business e2e","price":18000,"storeId":${STORE_ID},"options":[{"optionName":"기본","additionalPrice":0,"stockQuantity":10}],"shippingInfo":{"shippingFee":2500,"freeShippingThreshold":40000,"estimatedDays":2,"returnPolicy":"returnable"}}
JSON
)"

  create_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${PRODUCT_PORT}/api/products" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: ${SELLER_ID}" \
    -d "$product_payload")"
  create_body="$(extract_http_body "$create_raw")"
  create_status="$(extract_http_status "$create_raw")"
  ensure_http_status "hotdeal product create status" "$create_status" "200" "$create_body" || return 1
  ensure_json_success "hotdeal product create success" "$create_body" || return 1

  hotdeal_item_id="$(echo "$create_body" | jq -r '.data.id // empty')"
  if [[ -z "$hotdeal_item_id" || "$hotdeal_item_id" == "null" ]]; then
    fail "hotdeal item id resolved"
    return 1
  fi
  pass "hotdeal item id resolved (${hotdeal_item_id})"

  status_raw="$(curl -sS -w '\n%{http_code}' -X PATCH "http://127.0.0.1:${PRODUCT_PORT}/api/items/${hotdeal_item_id}/status" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: ${SELLER_ID}" \
    -d '{"status":"ON_SALE","reason":"hotdeal business e2e"}')"
  status_body="$(extract_http_body "$status_raw")"
  status_code="$(extract_http_status "$status_raw")"
  ensure_http_status "item status DRAFT->ON_SALE for hotdeal" "$status_code" "200" "$status_body" || return 1
  ensure_json_success "item status DRAFT->ON_SALE for hotdeal success" "$status_body" || return 1

  hotdeal_payload="$(cat <<JSON
{"itemId":${hotdeal_item_id},"discountRate":20,"maxQuantity":2,"maxPerUser":1}
JSON
)"
  hotdeal_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${HOTDEAL_PORT}/api/v1/hot-deals" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: ${SELLER_ID}" \
    -d "$hotdeal_payload")"
  hotdeal_body="$(extract_http_body "$hotdeal_raw")"
  hotdeal_status="$(extract_http_status "$hotdeal_raw")"
  ensure_http_status "hotdeal create status" "$hotdeal_status" "200" "$hotdeal_body" || return 1
  ensure_json_success "hotdeal create success" "$hotdeal_body" || return 1

  hotdeal_id="$(echo "$hotdeal_body" | jq -r '.data.id // empty')"
  if [[ -z "$hotdeal_id" || "$hotdeal_id" == "null" ]]; then
    fail "hotdeal id resolved"
    return 1
  fi
  pass "hotdeal id resolved (${hotdeal_id})"

  detail_raw="$(curl -sS -w '\n%{http_code}' "http://127.0.0.1:${HOTDEAL_PORT}/api/v1/hot-deals/${hotdeal_id}")"
  detail_body="$(extract_http_body "$detail_raw")"
  detail_status="$(extract_http_status "$detail_raw")"
  ensure_http_status "hotdeal detail status" "$detail_status" "200" "$detail_body" || return 1
  ensure_json_success "hotdeal detail success" "$detail_body" || return 1

  list_raw="$(curl -sS -w '\n%{http_code}' "http://127.0.0.1:${HOTDEAL_PORT}/api/v1/hot-deals?size=20")"
  list_body="$(extract_http_body "$list_raw")"
  list_status="$(extract_http_status "$list_raw")"
  ensure_http_status "hotdeal list status" "$list_status" "200" "$list_body" || return 1
  ensure_json_success "hotdeal list success" "$list_body" || return 1
  contains_hotdeal="$(echo "$list_body" | jq -r --arg id "$hotdeal_id" '((.data // []) | map(.id|tostring) | index($id)) != null')"
  if [[ "$contains_hotdeal" == "true" ]]; then
    pass "hotdeal list includes created hotdeal"
  else
    fail "hotdeal list includes created hotdeal"
    return 1
  fi

  queue_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${HOTDEAL_PORT}/api/v1/hot-deals/${hotdeal_id}/queue/enter" \
    -H "X-User-Id: ${BUYER_ID}")"
  queue_body="$(extract_http_body "$queue_raw")"
  queue_status="$(extract_http_status "$queue_raw")"
  ensure_http_status "hotdeal queue enter buyer1 status" "$queue_status" "200" "$queue_body" || return 1
  ensure_json_success "hotdeal queue enter buyer1 success" "$queue_body" || return 1
  wait_queue_admitted "$hotdeal_id" "$BUYER_ID" 60 || return 1

  purchase_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${HOTDEAL_PORT}/api/v1/hot-deals/${hotdeal_id}/purchase" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: ${BUYER_ID}" \
    -d '{"quantity":1}')"
  purchase_body="$(extract_http_body "$purchase_raw")"
  purchase_status="$(extract_http_status "$purchase_raw")"
  ensure_http_status "hotdeal purchase buyer1 status" "$purchase_status" "200" "$purchase_body" || return 1
  ensure_json_success "hotdeal purchase buyer1 success" "$purchase_body" || return 1

  repurchase_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${HOTDEAL_PORT}/api/v1/hot-deals/${hotdeal_id}/purchase" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: ${BUYER_ID}" \
    -d '{"quantity":1}')"
  repurchase_body="$(extract_http_body "$repurchase_raw")"
  repurchase_status="$(extract_http_status "$repurchase_raw")"
  ensure_http_status "hotdeal repurchase buyer1 rejected status" "$repurchase_status" "400" "$repurchase_body" || return 1
  ensure_error_code "hotdeal repurchase buyer1 rejected code" "$repurchase_body" "HOTDEAL-101"

  queue_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${HOTDEAL_PORT}/api/v1/hot-deals/${hotdeal_id}/queue/enter" \
    -H "X-User-Id: ${BUYER2_ID}")"
  queue_body="$(extract_http_body "$queue_raw")"
  queue_status="$(extract_http_status "$queue_raw")"
  ensure_http_status "hotdeal queue enter buyer2 status" "$queue_status" "200" "$queue_body" || return 1
  ensure_json_success "hotdeal queue enter buyer2 success" "$queue_body" || return 1
  wait_queue_admitted "$hotdeal_id" "$BUYER2_ID" 60 || return 1

  purchase2_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${HOTDEAL_PORT}/api/v1/hot-deals/${hotdeal_id}/purchase" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: ${BUYER2_ID}" \
    -d '{"quantity":1}')"
  purchase2_body="$(extract_http_body "$purchase2_raw")"
  purchase2_status="$(extract_http_status "$purchase2_raw")"
  ensure_http_status "hotdeal purchase buyer2 status" "$purchase2_status" "200" "$purchase2_body" || return 1
  ensure_json_success "hotdeal purchase buyer2 success" "$purchase2_body" || return 1

  queue_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${HOTDEAL_PORT}/api/v1/hot-deals/${hotdeal_id}/queue/enter" \
    -H "X-User-Id: ${BUYER3_ID}")"
  queue_body="$(extract_http_body "$queue_raw")"
  queue_status="$(extract_http_status "$queue_raw")"
  ensure_http_status "hotdeal queue enter buyer3 status" "$queue_status" "200" "$queue_body" || return 1
  ensure_json_success "hotdeal queue enter buyer3 success" "$queue_body" || return 1
  wait_queue_admitted "$hotdeal_id" "$BUYER3_ID" 60 || return 1

  soldout_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${HOTDEAL_PORT}/api/v1/hot-deals/${hotdeal_id}/purchase" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: ${BUYER3_ID}" \
    -d '{"quantity":1}')"
  soldout_body="$(extract_http_body "$soldout_raw")"
  soldout_status="$(extract_http_status "$soldout_raw")"
  ensure_http_status "hotdeal purchase sold-out rejected status" "$soldout_status" "409" "$soldout_body" || return 1
  ensure_error_code "hotdeal purchase sold-out rejected code" "$soldout_body" "HOTDEAL-005"

  pass "hotdeal sold-out business path verified by purchase rejection"
}

main() {
  require_cmd curl
  require_cmd jq
  require_cmd docker
  require_cmd lsof

  check_port_free "$PRODUCT_PORT" || true
  check_port_free "$STOCK_PORT" || true
  check_port_free "$FUNDING_PORT" || true
  check_port_free "$HOTDEAL_PORT" || true

  echo "[INFO] preparing docker infra"
  (cd "$ROOT/docker" && docker compose up -d postgres redis zookeeper kafka >/dev/null)
  pass "docker infra up (postgres/redis/zookeeper/kafka)"

  start_services

  wait_health "stock-service" "$STOCK_PORT"
  wait_health "product-service" "$PRODUCT_PORT"
  wait_health "funding-service" "$FUNDING_PORT"
  wait_health "hot-deal-service" "$HOTDEAL_PORT"

  wait_consumer_assignment "$STOCK_CONSUMER_GROUP" "item-events" "stock consumer assignment ready (item-events)"

  run_funding_business_flow
  run_hotdeal_business_flow

  echo "[INFO] summary: passes=${PASSES}, failures=${FAILURES}"
  if (( FAILURES > 0 )); then
    exit 1
  fi
}

main "$@"
