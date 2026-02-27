#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOG_DIR="${LOG_DIR:-/tmp/product_media_resilience_logs_$(date +%Y%m%d_%H%M%S)}"
mkdir -p "$LOG_DIR"

PRODUCT_A_PORT="${PRODUCT_A_PORT:-18284}"
PRODUCT_B_PORT="${PRODUCT_B_PORT:-18285}"
MEDIA_PORT="${MEDIA_PORT:-18294}"
SELLER_ID="${SELLER_ID:-910001}"
STORE_ID="${STORE_ID:-920001}"

RETRY_FIXED_DELAY_MS="${RETRY_FIXED_DELAY_MS:-1000}"
RETRY_MAX_RETRY_COUNT="${RETRY_MAX_RETRY_COUNT:-3}"
RETRY_BASE_DELAY_SECONDS="${RETRY_BASE_DELAY_SECONDS:-1}"
RETRY_MAX_DELAY_SECONDS="${RETRY_MAX_DELAY_SECONDS:-1}"
RETRY_STALE_SECONDS="${RETRY_STALE_SECONDS:-10}"

AWS_PROFILE_NAME="${AWS_PROFILE:-mzc}"
AWS_REGION_NAME="${AWS_REGION:-ap-northeast-2}"
MEDIA_BUCKET="${MEDIA_BUCKET:-team2-donmoa-media-raw}"
MEDIA_PREFIX="${MEDIA_PREFIX:-team2-donmoa-media}"
CF_COMMENT="${CF_DISTRIBUTION_COMMENT:-team2-donmoa-media}"
MEDIA_CLOUDFRONT_DOMAIN="${MEDIA_CLOUDFRONT_DOMAIN:-}"

PIDS=()
PRODUCT_A_PID=""
PRODUCT_B_PID=""
MEDIA_PID=""
FAILURES=0
PASSES=0

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
    echo "[ERR] required command not found: $cmd" >&2
    exit 1
  fi
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
    return 0
  fi
  fail "$label (expected=${expected}, actual=${actual})"
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
  for _ in {1..120}; do
    local status
    status="$(curl -sS --max-time 2 "http://127.0.0.1:${port}/actuator/health" | jq -r '.status' 2>/dev/null || true)"
    if [[ "$status" == "UP" ]]; then
      pass "$name health is UP"
      return 0
    fi
    sleep 2
  done
  fail "$name health timeout (port=${port})"
  return 1
}

wait_down() {
  local name="$1"
  local port="$2"
  for _ in {1..30}; do
    local code
    code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 2 "http://127.0.0.1:${port}/actuator/health" 2>/dev/null || true)"
    if [[ "$code" == "000" || "$code" == "503" || "$code" == "502" || "$code" == "500" ]]; then
      pass "$name became unavailable"
      return 0
    fi
    sleep 1
  done
  fail "$name unavailable check timeout"
  return 1
}

psql_exec() {
  local db="$1"
  local sql="$2"
  docker exec -i project03-postgres psql -U postgres -d "$db" -v ON_ERROR_STOP=1 -c "$sql" >/dev/null
}

psql_query() {
  local db="$1"
  local sql="$2"
  docker exec -i project03-postgres psql -U postgres -d "$db" -At -c "$sql"
}

resolve_media_domain() {
  if [[ -n "$MEDIA_CLOUDFRONT_DOMAIN" ]]; then
    echo "$MEDIA_CLOUDFRONT_DOMAIN"
    return 0
  fi

  if command -v aws >/dev/null 2>&1; then
    local domain
    domain="$(env AWS_PROFILE="$AWS_PROFILE_NAME" aws cloudfront list-distributions \
      --query "DistributionList.Items[?Comment=='${CF_COMMENT}'].DomainName | [0]" \
      --output text 2>/dev/null || true)"
    if [[ -n "$domain" && "$domain" != "None" ]]; then
      echo "https://${domain}"
      return 0
    fi
  fi

  echo "https://${MEDIA_BUCKET}.s3.${AWS_REGION_NAME}.amazonaws.com"
}

start_media() {
  echo "[INFO] starting media-api on ${MEDIA_PORT}"
  (
    cd "$ROOT"
    env \
      AWS_PROFILE="$AWS_PROFILE_NAME" \
      AWS_REGION="$AWS_REGION_NAME" \
      GRADLE_USER_HOME=/tmp/.gradle-codex \
      SERVER_PORT="$MEDIA_PORT" \
      APP_GATEWAY_SECURITY_ENABLED=false \
      MEDIA_S3_REGION="$AWS_REGION_NAME" \
      MEDIA_S3_BUCKET="$MEDIA_BUCKET" \
      MEDIA_S3_KEY_PREFIX="$MEDIA_PREFIX" \
      MEDIA_CLOUDFRONT_DOMAIN="$MEDIA_DOMAIN" \
      ./gradlew :servers:services:media-api:bootRun --no-daemon >>"$LOG_DIR/media-api.log" 2>&1
  ) &
  MEDIA_PID="$!"
  PIDS+=("$MEDIA_PID")
}

stop_media() {
  if [[ -n "${MEDIA_PID:-}" ]] && kill -0 "$MEDIA_PID" 2>/dev/null; then
    kill "$MEDIA_PID" 2>/dev/null || true
    wait "$MEDIA_PID" 2>/dev/null || true
  fi
  MEDIA_PID=""

  local leftovers
  leftovers="$(lsof -tiTCP:"$MEDIA_PORT" -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -n "$leftovers" ]]; then
    while IFS= read -r pid; do
      [[ -z "$pid" ]] && continue
      kill "$pid" 2>/dev/null || true
      wait "$pid" 2>/dev/null || true
    done <<< "$leftovers"
  fi
}

start_product_a() {
  echo "[INFO] starting product-service A on ${PRODUCT_A_PORT}"
  (
    cd "$ROOT"
    env \
      GRADLE_USER_HOME=/tmp/.gradle-codex \
      SERVER_PORT="$PRODUCT_A_PORT" \
      SNOWFLAKE_WORKER_ID=11 \
      APP_GATEWAY_SECURITY_ENABLED=false \
      MEDIA_SERVICE_URL="http://127.0.0.1:${MEDIA_PORT}" \
      APP_MEDIA_LINK_SYNC_RETRY_FIXED_DELAY_MS="$RETRY_FIXED_DELAY_MS" \
      APP_MEDIA_LINK_SYNC_RETRY_MAX_RETRY_COUNT="$RETRY_MAX_RETRY_COUNT" \
      APP_MEDIA_LINK_SYNC_RETRY_BASE_DELAY_SECONDS="$RETRY_BASE_DELAY_SECONDS" \
      APP_MEDIA_LINK_SYNC_RETRY_MAX_DELAY_SECONDS="$RETRY_MAX_DELAY_SECONDS" \
      APP_MEDIA_LINK_SYNC_RETRY_STALE_SECONDS="$RETRY_STALE_SECONDS" \
      ./gradlew :servers:services:product:bootRun --no-daemon >>"$LOG_DIR/product-a.log" 2>&1
  ) &
  PRODUCT_A_PID="$!"
  PIDS+=("$PRODUCT_A_PID")
}

start_product_b() {
  echo "[INFO] starting product-service B on ${PRODUCT_B_PORT}"
  (
    cd "$ROOT"
    env \
      GRADLE_USER_HOME=/tmp/.gradle-codex \
      SERVER_PORT="$PRODUCT_B_PORT" \
      SNOWFLAKE_WORKER_ID=12 \
      APP_GATEWAY_SECURITY_ENABLED=false \
      MEDIA_SERVICE_URL="http://127.0.0.1:${MEDIA_PORT}" \
      APP_MEDIA_LINK_SYNC_RETRY_FIXED_DELAY_MS="$RETRY_FIXED_DELAY_MS" \
      APP_MEDIA_LINK_SYNC_RETRY_MAX_RETRY_COUNT="$RETRY_MAX_RETRY_COUNT" \
      APP_MEDIA_LINK_SYNC_RETRY_BASE_DELAY_SECONDS="$RETRY_BASE_DELAY_SECONDS" \
      APP_MEDIA_LINK_SYNC_RETRY_MAX_DELAY_SECONDS="$RETRY_MAX_DELAY_SECONDS" \
      APP_MEDIA_LINK_SYNC_RETRY_STALE_SECONDS="$RETRY_STALE_SECONDS" \
      ./gradlew :servers:services:product:bootRun --no-daemon >>"$LOG_DIR/product-b.log" 2>&1
  ) &
  PRODUCT_B_PID="$!"
  PIDS+=("$PRODUCT_B_PID")
}

create_media_and_confirm() {
  local result_var="$1"
  local label="$2"
  local tmp_file
  tmp_file="$(mktemp)"
  printf '%s' "${label}-$(date +%s%N)" >"$tmp_file"
  local file_size
  file_size="$(wc -c <"$tmp_file" | tr -d ' ')"

  local intent_raw intent_body intent_status
  intent_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${MEDIA_PORT}/api/v1/media/upload-intents" \
    -H "Content-Type: application/json" \
    -d "{\"fileName\":\"${label}.jpg\",\"contentType\":\"image/jpeg\",\"fileSize\":${file_size}}")"
  intent_body="$(extract_http_body "$intent_raw")"
  intent_status="$(extract_http_status "$intent_raw")"
  ensure_http_status "${label} upload-intent status" "$intent_status" "200" || return 1
  ensure_json_success "${label} upload-intent success" "$intent_body" || return 1

  local media_id presigned_url upload_token
  media_id="$(echo "$intent_body" | jq -r '.data.mediaId // empty')"
  presigned_url="$(echo "$intent_body" | jq -r '.data.presignedUrl // empty')"
  upload_token="$(echo "$intent_body" | jq -r '.data.uploadToken // empty')"
  if [[ -z "$media_id" || -z "$presigned_url" || -z "$upload_token" ]]; then
    fail "${label} upload-intent response missing data"
    return 1
  fi

  local put_status
  put_status="$(curl -sS -o /dev/null -w '%{http_code}' -X PUT "$presigned_url" \
    -H "Content-Type: image/jpeg" \
    --data-binary @"$tmp_file")"
  if [[ "$put_status" == "200" || "$put_status" == "204" ]]; then
    pass "${label} presigned upload"
  else
    fail "${label} presigned upload (status=${put_status})"
    return 1
  fi

  local confirm_raw confirm_body confirm_status
  confirm_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${MEDIA_PORT}/api/v1/media/confirm" \
    -H "Content-Type: application/json" \
    -d "{\"mediaId\":${media_id},\"uploadToken\":\"${upload_token}\"}")"
  confirm_body="$(extract_http_body "$confirm_raw")"
  confirm_status="$(extract_http_status "$confirm_raw")"
  ensure_http_status "${label} confirm status" "$confirm_status" "200" || return 1
  ensure_json_success "${label} confirm success" "$confirm_body" || return 1

  rm -f "$tmp_file"
  printf -v "$result_var" '%s' "$media_id"
}

create_product_item() {
  local result_var="$1"
  local ts
  ts="$(date +%s)"
  local payload
  payload="$(cat <<JSON
{
  "title": "Resilience Product ${ts}",
  "description": "product-media resilience",
  "price": 15000,
  "storeId": ${STORE_ID},
  "options": [
    {"optionName":"기본","additionalPrice":0,"stockQuantity":15}
  ],
  "shippingInfo": {
    "shippingFee": 3000,
    "freeShippingThreshold": 50000,
    "estimatedDays": 2,
    "returnPolicy": "7일 내 환불"
  }
}
JSON
)"

  local raw body status
  raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${PRODUCT_A_PORT}/api/products" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: ${SELLER_ID}" \
    -d "$payload")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "create product status" "$status" "200" || return 1
  ensure_json_success "create product success" "$body" || return 1
  local created_item_id
  created_item_id="$(echo "$body" | jq -r '.data.id // empty')"
  if [[ -z "$created_item_id" ]]; then
    fail "create product itemId missing"
    return 1
  fi
  pass "create product itemId resolved (${created_item_id})"
  printf -v "$result_var" '%s' "$created_item_id"
}

add_three_images() {
  local item_id="$1"
  local media1_var="$2"
  local media2_var="$3"
  local media3_var="$4"

  local created_m1 created_m2 created_m3
  create_media_and_confirm created_m1 "res-m1" || return 1
  create_media_and_confirm created_m2 "res-m2" || return 1
  create_media_and_confirm created_m3 "res-m3" || return 1

  local payload
  payload="$(cat <<JSON
[
  {"mediaId": ${created_m1}, "sortOrder": 0, "isThumbnail": true},
  {"mediaId": ${created_m2}, "sortOrder": 1, "isThumbnail": false},
  {"mediaId": ${created_m3}, "sortOrder": 2, "isThumbnail": false}
]
JSON
)"
  local raw body status
  raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${PRODUCT_A_PORT}/api/items/${item_id}/images" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: ${SELLER_ID}" \
    -d "$payload")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "add images status" "$status" "200" || return 1
  ensure_json_success "add images success" "$body" || return 1

  printf -v "$media1_var" '%s' "$created_m1"
  printf -v "$media2_var" '%s' "$created_m2"
  printf -v "$media3_var" '%s' "$created_m3"
}

fetch_image_ids() {
  local item_id="$1"
  local thumb_var="$2"
  local gallery1_var="$3"
  local gallery2_var="$4"
  local detail
  detail="$(curl -sS "http://127.0.0.1:${PRODUCT_A_PORT}/api/products/${item_id}")"
  ensure_json_success "fetch product detail success" "$detail" || return 1

  local thumb gallery1 gallery2
  thumb="$(echo "$detail" | jq -r '.data.images.thumbnail.id // empty')"
  gallery1="$(echo "$detail" | jq -r '.data.images.gallery[0].id // empty')"
  gallery2="$(echo "$detail" | jq -r '.data.images.gallery[1].id // empty')"
  if [[ -z "$thumb" || -z "$gallery1" || -z "$gallery2" ]]; then
    fail "fetch image ids precondition failed"
    return 1
  fi
  printf -v "$thumb_var" '%s' "$thumb"
  printf -v "$gallery1_var" '%s' "$gallery1"
  printf -v "$gallery2_var" '%s' "$gallery2"
}

reorder_images() {
  local port="$1"
  local item_id="$2"
  local order_json="$3"
  local raw body status
  raw="$(curl -sS -w '\n%{http_code}' -X PUT "http://127.0.0.1:${port}/api/items/${item_id}/images/reorder" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: ${SELLER_ID}" \
    -d "$order_json")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "reorder(${port}) status" "$status" "200" || return 1
  ensure_json_success "reorder(${port}) success" "$body" || return 1
}

wait_item_sync_status() {
  local item_id="$1"
  local target_status="$2"
  local timeout_seconds="$3"
  local elapsed=0
  while [[ "$elapsed" -lt "$timeout_seconds" ]]; do
    local status
    status="$(psql_query product_db "SELECT COALESCE(status, '') FROM item_media_link_sync_tasks WHERE item_id=${item_id};")"
    if [[ "$status" == "$target_status" ]]; then
      pass "item sync status reached ${target_status} (itemId=${item_id})"
      return 0
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done
  local final_status
  final_status="$(psql_query product_db "SELECT COALESCE(status, '') FROM item_media_link_sync_tasks WHERE item_id=${item_id};")"
  fail "item sync status ${target_status} timeout (itemId=${item_id}, final=${final_status})"
  return 1
}

reset_data() {
  echo "[INFO] resetting media/product data"
  psql_exec media_db "TRUNCATE TABLE media_links, media_files, processed_events, dead_letter_messages, outbox_messages RESTART IDENTITY CASCADE;"
  psql_exec product_db "TRUNCATE TABLE item_images, item_goods_links, item_options, shipping_infos, cast_members, seat_grades, performances, item_status_histories, item_media_link_sync_tasks, items, processed_events, dead_letter_messages, outbox_messages RESTART IDENTITY CASCADE;"
  pass "reset data complete"
}

run_long_outage_failed_case() {
  echo "[INFO] case1: long outage -> FAILED -> recovery"
  reset_data

  local item_id m1 m2 m3 thumb_id g1_id g2_id
  create_product_item item_id || return 1
  add_three_images "$item_id" m1 m2 m3 || return 1
  fetch_image_ids "$item_id" thumb_id g1_id g2_id || return 1

  stop_media
  wait_down "media-api" "$MEDIA_PORT" || true

  local reorder_down
  reorder_down="[${thumb_id},${g2_id},${g1_id}]"
  reorder_images "$PRODUCT_A_PORT" "$item_id" "$reorder_down" || return 1

  wait_item_sync_status "$item_id" "FAILED" 30 || return 1
  local retry_count
  retry_count="$(psql_query product_db "SELECT retry_count FROM item_media_link_sync_tasks WHERE item_id=${item_id};")"
  if [[ "$retry_count" -ge "$RETRY_MAX_RETRY_COUNT" ]]; then
    pass "FAILED retry_count reached threshold (retryCount=${retry_count})"
  else
    fail "FAILED retry_count below threshold (retryCount=${retry_count}, threshold=${RETRY_MAX_RETRY_COUNT})"
  fi

  start_media
  wait_health "media-api" "$MEDIA_PORT" || return 1

  local detail_after_failed
  detail_after_failed="$(curl -sS "http://127.0.0.1:${PRODUCT_A_PORT}/api/products/${item_id}")"
  ensure_json_success "detail after failed sync success" "$detail_after_failed" || return 1
  local cur_thumb cur_g1 cur_g2
  cur_thumb="$(echo "$detail_after_failed" | jq -r '.data.images.thumbnail.id // empty')"
  cur_g1="$(echo "$detail_after_failed" | jq -r '.data.images.gallery[0].id // empty')"
  cur_g2="$(echo "$detail_after_failed" | jq -r '.data.images.gallery[1].id // empty')"
  if [[ -z "$cur_thumb" || -z "$cur_g1" || -z "$cur_g2" ]]; then
    fail "recovery precondition missing ids"
    return 1
  fi
  local reorder_recovery
  reorder_recovery="[${cur_thumb},${cur_g2},${cur_g1}]"
  reorder_images "$PRODUCT_A_PORT" "$item_id" "$reorder_recovery" || return 1

  wait_item_sync_status "$item_id" "COMPLETED" 30 || return 1
  local active_links thumb_links
  active_links="$(psql_query media_db "SELECT count(*) FROM media_links WHERE owner_type='ITEM' AND owner_id=${item_id} AND deleted_at IS NULL;")"
  thumb_links="$(psql_query media_db "SELECT count(*) FROM media_links WHERE owner_type='ITEM' AND owner_id=${item_id} AND usage_type='THUMBNAIL' AND deleted_at IS NULL;")"
  if [[ "$active_links" == "3" && "$thumb_links" == "1" ]]; then
    pass "media links recovered after FAILED task"
  else
    fail "media links recovered after FAILED task (active=${active_links}, thumb=${thumb_links})"
  fi
}

run_multi_instance_high_load_case() {
  echo "[INFO] case2: high-load + multi-instance concurrency"
  reset_data

  local item_id m1 m2 m3 thumb_id g1_id g2_id
  create_product_item item_id || return 1
  add_three_images "$item_id" m1 m2 m3 || return 1
  fetch_image_ids "$item_id" thumb_id g1_id g2_id || return 1

  local order_a order_b
  order_a="[${thumb_id},${g2_id},${g1_id}]"
  order_b="[${thumb_id},${g1_id},${g2_id}]"

  local result_file
  result_file="$(mktemp)"
  local rounds=32
  local max_parallel=8
  local request_pids=()
  for i in $(seq 1 "$rounds"); do
    while [[ "$(jobs -rp | wc -l | tr -d ' ')" -ge "$max_parallel" ]]; do
      sleep 0.1
    done
    (
      local port order raw body status ok
      if (( i % 2 == 0 )); then
        port="$PRODUCT_A_PORT"
        order="$order_a"
      else
        port="$PRODUCT_B_PORT"
        order="$order_b"
      fi
      raw="$(curl -sS --connect-timeout 2 --max-time 8 -w '\n%{http_code}' -X PUT "http://127.0.0.1:${port}/api/items/${item_id}/images/reorder" \
        -H "Content-Type: application/json" \
        -H "X-User-Id: ${SELLER_ID}" \
        -d "$order")"
      body="$(extract_http_body "$raw")"
      status="$(extract_http_status "$raw")"
      ok="$(echo "$body" | jq -r '.success // false')"
      if [[ "$status" == "200" && "$ok" == "true" ]]; then
        echo "OK" >> "$result_file"
      else
        echo "FAIL status=${status}" >> "$result_file"
      fi
    ) &
    request_pids+=("$!")
  done
  for pid in "${request_pids[@]}"; do
    wait "$pid"
  done

  local fail_count
  fail_count="$(grep -c '^FAIL' "$result_file" || true)"
  rm -f "$result_file"
  if [[ "$fail_count" == "0" ]]; then
    pass "high-load concurrent reorder requests all succeeded (rounds=${rounds})"
  else
    fail "high-load concurrent reorder failed count=${fail_count}"
  fi

  local active_images duplicate_sort thumbnail_count
  active_images="$(psql_query product_db "SELECT count(*) FROM item_images WHERE item_id=${item_id} AND deleted_at IS NULL;")"
  duplicate_sort="$(psql_query product_db "SELECT count(*) FROM (SELECT sort_order FROM item_images WHERE item_id=${item_id} AND deleted_at IS NULL GROUP BY sort_order HAVING count(*) > 1) t;")"
  thumbnail_count="$(psql_query product_db "SELECT count(*) FROM item_images WHERE item_id=${item_id} AND deleted_at IS NULL AND is_thumbnail=true;")"

  local active_links thumb_links gallery_dup_sort sync_wait=0
  while [[ "$sync_wait" -lt 20 ]]; do
    active_links="$(psql_query media_db "SELECT count(*) FROM media_links WHERE owner_type='ITEM' AND owner_id=${item_id} AND deleted_at IS NULL;")"
    thumb_links="$(psql_query media_db "SELECT count(*) FROM media_links WHERE owner_type='ITEM' AND owner_id=${item_id} AND usage_type='THUMBNAIL' AND deleted_at IS NULL;")"
    if [[ "$active_links" == "3" && "$thumb_links" == "1" ]]; then
      break
    fi
    sleep 1
    sync_wait=$((sync_wait + 1))
  done
  gallery_dup_sort="$(psql_query media_db "SELECT count(*) FROM (SELECT sort_order FROM media_links WHERE owner_type='ITEM' AND owner_id=${item_id} AND usage_type='GALLERY' AND deleted_at IS NULL GROUP BY sort_order HAVING count(*) > 1) t;")"

  [[ "$active_images" == "3" ]] && pass "multi-instance invariant: active item_images=3" || fail "multi-instance invariant: active item_images=3 (actual=${active_images})"
  [[ "$duplicate_sort" == "0" ]] && pass "multi-instance invariant: no duplicate item sort_order" || fail "multi-instance invariant: duplicate item sort_order=${duplicate_sort}"
  [[ "$thumbnail_count" == "1" ]] && pass "multi-instance invariant: single thumbnail image" || fail "multi-instance invariant: thumbnail count=${thumbnail_count}"
  [[ "$active_links" == "3" ]] && pass "multi-instance invariant: active media_links=3" || fail "multi-instance invariant: active media_links=${active_links}"
  [[ "$thumb_links" == "1" ]] && pass "multi-instance invariant: single thumbnail link" || fail "multi-instance invariant: thumbnail links=${thumb_links}"
  [[ "$gallery_dup_sort" == "0" ]] && pass "multi-instance invariant: unique gallery sort_order" || fail "multi-instance invariant: duplicate gallery sort=${gallery_dup_sort}"
}

require_cmd curl
require_cmd jq
require_cmd docker
require_cmd lsof
require_cmd mktemp
require_cmd wc

check_port_free "$PRODUCT_A_PORT"
check_port_free "$PRODUCT_B_PORT"
check_port_free "$MEDIA_PORT"

echo "[INFO] preparing infra"
(cd "$ROOT/docker" && docker compose up -d postgres redis zookeeper kafka mariadb elasticsearch >/dev/null)

MEDIA_DOMAIN="$(resolve_media_domain)"
echo "[INFO] media public domain: ${MEDIA_DOMAIN}"

start_media
start_product_a

wait_health "media-api" "$MEDIA_PORT"
wait_health "product-service A" "$PRODUCT_A_PORT"

run_long_outage_failed_case

start_product_b
wait_health "product-service B" "$PRODUCT_B_PORT"

run_multi_instance_high_load_case

echo "[INFO] result passes=${PASSES} failures=${FAILURES}"
if [[ "$FAILURES" -gt 0 ]]; then
  exit 1
fi

pass "product/media resilience verify passed"
