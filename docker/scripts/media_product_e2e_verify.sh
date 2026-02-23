#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOG_DIR="/tmp/media_product_e2e_logs_$(date +%Y%m%d_%H%M%S)"
mkdir -p "$LOG_DIR"

PRODUCT_PORT="${PRODUCT_PORT:-18084}"
MEDIA_PORT="${MEDIA_PORT:-18094}"
SELLER_ID="${SELLER_ID:-910001}"
STORE_ID="${STORE_ID:-920001}"

AWS_PROFILE_NAME="${AWS_PROFILE:-mzc}"
AWS_REGION_NAME="${AWS_REGION:-ap-northeast-2}"
MEDIA_BUCKET="${MEDIA_BUCKET:-team2-donmoa-media-raw}"
MEDIA_PREFIX="${MEDIA_PREFIX:-team2-donmoa-media}"
CF_COMMENT="${CF_DISTRIBUTION_COMMENT:-team2-donmoa-media}"
MEDIA_CLOUDFRONT_DOMAIN="${MEDIA_CLOUDFRONT_DOMAIN:-}"

PIDS=()
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
  echo "[INFO] service logs: $LOG_DIR"
}
trap cleanup EXIT

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
  fail "$name health check timeout (port=${port})"
  return 1
}

check_port_free() {
  local port="$1"
  if lsof -tiTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    fail "port $port is already in use"
    return 1
  fi
  pass "port $port is free"
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

assert_images_empty() {
  local label="$1"
  local body="$2"
  local thumbnail_null gallery_count
  thumbnail_null="$(echo "$body" | jq -r '.data.images.thumbnail == null')"
  gallery_count="$(echo "$body" | jq -r '(.data.images.gallery // []) | length')"
  if [[ "$thumbnail_null" == "true" && "$gallery_count" == "0" ]]; then
    pass "$label"
  else
    fail "$label (thumbnailNull=${thumbnail_null}, galleryCount=${gallery_count})"
  fi
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
  ensure_http_status "${label} upload-intent status" "$intent_status" "200"
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
  ensure_http_status "${label} confirm status" "$confirm_status" "200"
  ensure_json_success "${label} confirm success" "$confirm_body" || return 1

  local media_url
  media_url="$(echo "$confirm_body" | jq -r '.data.mediaUrl // empty')"
  if [[ -n "$media_url" ]]; then
    pass "${label} confirm mediaUrl issued"
  else
    fail "${label} confirm mediaUrl missing"
    return 1
  fi

  rm -f "$tmp_file"
  printf -v "$result_var" '%s' "$media_id"
}

create_item_without_image() {
  local result_var="$1"
  local kind="$2"
  local ts
  ts="$(date +%s)"

  local endpoint payload label
  case "$kind" in
    product)
      endpoint="/api/products"
      label="product"
      payload="$(cat <<JSON
{
  "title": "E2E Product ${ts}",
  "description": "media sync e2e product",
  "price": 12000,
  "storeId": ${STORE_ID},
  "options": [
    {"optionName":"기본","additionalPrice":0,"stockQuantity":10}
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
      ;;
    goods)
      endpoint="/api/goods"
      label="goods"
      payload="$(cat <<JSON
{
  "title": "E2E Goods ${ts}",
  "description": "media sync e2e goods",
  "price": 22000,
  "storeId": ${STORE_ID},
  "options": [
    {"optionName":"M","additionalPrice":0,"stockQuantity":8},
    {"optionName":"L","additionalPrice":1000,"stockQuantity":5}
  ],
  "shippingInfo": {
    "shippingFee": 2500,
    "freeShippingThreshold": 40000,
    "estimatedDays": 3,
    "returnPolicy": "교환/환불 가능"
  },
  "linkedPerformanceItemIds": []
}
JSON
)"
      ;;
    performance)
      endpoint="/api/performances"
      label="performance"
      payload="$(cat <<JSON
{
  "title": "E2E Performance ${ts}",
  "description": "media sync e2e performance",
  "price": 55000,
  "storeId": ${STORE_ID},
  "venue": "테스트홀",
  "performanceDate": "2030-01-10",
  "performanceTime": "19:30:00",
  "totalSeats": 120,
  "seatGrades": [
    {"gradeName":"R","price":65000,"totalQuantity":60,"fundingQuantity":10},
    {"gradeName":"S","price":50000,"totalQuantity":60,"fundingQuantity":5}
  ],
  "castMembers": [
    {"name":"테스트 배우","role":"주연","profileImageUrl":"https://example.com/cast.jpg"}
  ]
}
JSON
)"
      ;;
    *)
      fail "unknown item kind: $kind"
      return 1
      ;;
  esac

  local raw body status
  raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${PRODUCT_PORT}${endpoint}" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: ${SELLER_ID}" \
    -d "$payload")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "${label} create status" "$status" "200"
  ensure_json_success "${label} create success" "$body" || return 1
  assert_images_empty "${label} create without images" "$body"

  local item_id
  item_id="$(echo "$body" | jq -r '.data.id // empty')"
  if [[ -z "$item_id" ]]; then
    fail "${label} create itemId missing"
    return 1
  fi
  pass "${label} create itemId resolved (${item_id})"
  printf -v "$result_var" '%s' "$item_id"
}

verify_add_and_remove_all_images() {
  local kind="$1"
  local item_id="$2"
  local detail_endpoint="$3"

  local m1 m2 m3
  create_media_and_confirm m1 "${kind}-m1" || return 1
  create_media_and_confirm m2 "${kind}-m2" || return 1
  create_media_and_confirm m3 "${kind}-m3" || return 1

  local add_payload add_raw add_body add_status
  add_payload="$(cat <<JSON
[
  {"mediaId": ${m1}, "sortOrder": 0, "isThumbnail": true},
  {"mediaId": ${m2}, "sortOrder": 1, "isThumbnail": false},
  {"mediaId": ${m3}, "sortOrder": 2, "isThumbnail": false}
]
JSON
)"
  add_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${PRODUCT_PORT}/api/items/${item_id}/images" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: ${SELLER_ID}" \
    -d "$add_payload")"
  add_body="$(extract_http_body "$add_raw")"
  add_status="$(extract_http_status "$add_raw")"
  ensure_http_status "${kind} add images status" "$add_status" "200"
  ensure_json_success "${kind} add images success" "$add_body" || return 1

  local added_count
  added_count="$(echo "$add_body" | jq -r '(.data // []) | length')"
  if [[ "$added_count" == "3" ]]; then
    pass "${kind} added image count is 3"
  else
    fail "${kind} added image count is 3 (actual=${added_count})"
  fi

  local active_media_links
  active_media_links="$(psql_query media_db "SELECT count(*) FROM media_links WHERE owner_type='ITEM' AND owner_id=${item_id} AND deleted_at IS NULL;")"
  if [[ "$active_media_links" == "3" ]]; then
    pass "${kind} media_links active count after add is 3"
  else
    fail "${kind} media_links active count after add is 3 (actual=${active_media_links})"
  fi

  local detail_after_add
  detail_after_add="$(curl -sS "http://127.0.0.1:${PRODUCT_PORT}${detail_endpoint}")"
  ensure_json_success "${kind} detail after add success" "$detail_after_add" || return 1
  local thumb_media_id gallery_count
  thumb_media_id="$(echo "$detail_after_add" | jq -r '.data.images.thumbnail.mediaId // empty')"
  gallery_count="$(echo "$detail_after_add" | jq -r '(.data.images.gallery // []) | length')"
  if [[ "$thumb_media_id" == "$m1" && "$gallery_count" == "2" ]]; then
    pass "${kind} detail after add (thumbnail + gallery) verified"
  else
    fail "${kind} detail after add mismatch (thumbnail=${thumb_media_id}, gallery=${gallery_count})"
  fi

  local image_ids=()
  while IFS= read -r image_id; do
    if [[ -n "$image_id" ]]; then
      image_ids+=("$image_id")
    fi
  done < <(echo "$add_body" | jq -r '.data[].id')

  if [[ "${#image_ids[@]}" -eq 0 ]]; then
    fail "${kind} image id extraction failed"
    return 1
  fi

  for image_id in "${image_ids[@]}"; do
    local del_raw del_body del_status
    del_raw="$(curl -sS -w '\n%{http_code}' -X DELETE "http://127.0.0.1:${PRODUCT_PORT}/api/items/images/${image_id}" \
      -H "X-User-Id: ${SELLER_ID}")"
    del_body="$(extract_http_body "$del_raw")"
    del_status="$(extract_http_status "$del_raw")"
    ensure_http_status "${kind} delete image(${image_id}) status" "$del_status" "200"
    ensure_json_success "${kind} delete image(${image_id}) success" "$del_body" || return 1
  done

  local detail_after_delete
  detail_after_delete="$(curl -sS "http://127.0.0.1:${PRODUCT_PORT}${detail_endpoint}")"
  ensure_json_success "${kind} detail after deleting all images success" "$detail_after_delete" || return 1
  assert_images_empty "${kind} detail after deleting all images is empty" "$detail_after_delete"

  local active_item_images thumbnail_media_id_after
  active_item_images="$(psql_query product_db "SELECT count(*) FROM item_images WHERE item_id=${item_id} AND deleted_at IS NULL;")"
  thumbnail_media_id_after="$(psql_query product_db "SELECT COALESCE(thumbnail_media_id::text, '') FROM items WHERE id=${item_id};")"
  active_media_links="$(psql_query media_db "SELECT count(*) FROM media_links WHERE owner_type='ITEM' AND owner_id=${item_id} AND deleted_at IS NULL;")"

  if [[ "$active_item_images" == "0" ]]; then
    pass "${kind} item_images active count after delete-all is 0"
  else
    fail "${kind} item_images active count after delete-all is 0 (actual=${active_item_images})"
  fi

  if [[ -z "$thumbnail_media_id_after" ]]; then
    pass "${kind} item thumbnail_media_id cleared"
  else
    fail "${kind} item thumbnail_media_id cleared (actual=${thumbnail_media_id_after})"
  fi

  if [[ "$active_media_links" == "0" ]]; then
    pass "${kind} media_links active count after delete-all is 0"
  else
    fail "${kind} media_links active count after delete-all is 0 (actual=${active_media_links})"
  fi
}

verify_item_delete_clears_links() {
  local kind="$1"
  local item_id="$2"
  local delete_endpoint="$3"

  local m1 m2
  create_media_and_confirm m1 "${kind}-del-m1" || return 1
  create_media_and_confirm m2 "${kind}-del-m2" || return 1

  local add_payload add_raw add_body add_status
  add_payload="$(cat <<JSON
[
  {"mediaId": ${m1}, "sortOrder": 0, "isThumbnail": true},
  {"mediaId": ${m2}, "sortOrder": 1, "isThumbnail": false}
]
JSON
)"
  add_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${PRODUCT_PORT}/api/items/${item_id}/images" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: ${SELLER_ID}" \
    -d "$add_payload")"
  add_body="$(extract_http_body "$add_raw")"
  add_status="$(extract_http_status "$add_raw")"
  ensure_http_status "${kind} delete-flow add images status" "$add_status" "200"
  ensure_json_success "${kind} delete-flow add images success" "$add_body" || return 1

  local active_media_links
  active_media_links="$(psql_query media_db "SELECT count(*) FROM media_links WHERE owner_type='ITEM' AND owner_id=${item_id} AND deleted_at IS NULL;")"
  if [[ "$active_media_links" == "2" ]]; then
    pass "${kind} delete-flow media_links active count before delete is 2"
  else
    fail "${kind} delete-flow media_links active count before delete is 2 (actual=${active_media_links})"
  fi

  local delete_raw delete_body delete_status
  delete_raw="$(curl -sS -w '\n%{http_code}' -X DELETE "http://127.0.0.1:${PRODUCT_PORT}${delete_endpoint}" \
    -H "X-User-Id: ${SELLER_ID}")"
  delete_body="$(extract_http_body "$delete_raw")"
  delete_status="$(extract_http_status "$delete_raw")"
  ensure_http_status "${kind} delete item status" "$delete_status" "200"
  ensure_json_success "${kind} delete item success" "$delete_body" || return 1

  active_media_links="$(psql_query media_db "SELECT count(*) FROM media_links WHERE owner_type='ITEM' AND owner_id=${item_id} AND deleted_at IS NULL;")"
  if [[ "$active_media_links" == "0" ]]; then
    pass "${kind} delete-flow media_links active count after delete is 0"
  else
    fail "${kind} delete-flow media_links active count after delete is 0 (actual=${active_media_links})"
  fi

  local item_deleted
  item_deleted="$(psql_query product_db "SELECT CASE WHEN deleted_at IS NULL THEN 'false' ELSE 'true' END FROM items WHERE id=${item_id};")"
  if [[ "$item_deleted" == "true" ]]; then
    pass "${kind} item soft delete confirmed"
  else
    fail "${kind} item soft delete confirmed (actual=${item_deleted})"
  fi
}

require_cmd curl
require_cmd jq
require_cmd docker
require_cmd lsof

echo "[INFO] preparing infra"
(cd "$ROOT/docker" && docker compose up -d >/dev/null)

check_port_free "$PRODUCT_PORT"
check_port_free "$MEDIA_PORT"

MEDIA_DOMAIN="$(resolve_media_domain)"
echo "[INFO] media public domain: $MEDIA_DOMAIN"

echo "[INFO] starting media-api and product-service"
(
  cd "$ROOT"
  env AWS_PROFILE="$AWS_PROFILE_NAME" \
      AWS_REGION="$AWS_REGION_NAME" \
      GRADLE_USER_HOME=/tmp/.gradle-codex \
      SERVER_PORT="$MEDIA_PORT" \
      APP_GATEWAY_SECURITY_ENABLED=false \
      MEDIA_S3_REGION="$AWS_REGION_NAME" \
      MEDIA_S3_BUCKET="$MEDIA_BUCKET" \
      MEDIA_S3_KEY_PREFIX="$MEDIA_PREFIX" \
      MEDIA_CLOUDFRONT_DOMAIN="$MEDIA_DOMAIN" \
      ./gradlew :servers:services:media-api:bootRun --no-daemon >"$LOG_DIR/media-api.log" 2>&1
) &
PIDS+=("$!")

(
  cd "$ROOT"
  env GRADLE_USER_HOME=/tmp/.gradle-codex \
      SERVER_PORT="$PRODUCT_PORT" \
      APP_GATEWAY_SECURITY_ENABLED=false \
      MEDIA_SERVICE_URL="http://127.0.0.1:${MEDIA_PORT}" \
      ./gradlew :servers:services:product:bootRun --no-daemon >"$LOG_DIR/product.log" 2>&1
) &
PIDS+=("$!")

wait_health "media-api" "$MEDIA_PORT"
wait_health "product-service" "$PRODUCT_PORT"

echo "[INFO] resetting test data"
psql_exec media_db "TRUNCATE TABLE media_links, media_files, processed_events, dead_letter_messages, outbox_messages RESTART IDENTITY CASCADE;"
psql_exec product_db "TRUNCATE TABLE item_images, item_goods_links, item_options, shipping_infos, cast_members, seat_grades, performances, item_status_histories, item_media_link_sync_tasks, items, processed_events, dead_letter_messages, outbox_messages RESTART IDENTITY CASCADE;"
pass "reset media/product test data"

product_item_id=""
goods_item_id=""
performance_item_id=""

create_item_without_image product_item_id product || true
verify_add_and_remove_all_images product "$product_item_id" "/api/products/${product_item_id}" || true
product_delete_item_id=""
create_item_without_image product_delete_item_id product || true
verify_item_delete_clears_links product "$product_delete_item_id" "/api/products/${product_delete_item_id}" || true

create_item_without_image goods_item_id goods || true
verify_add_and_remove_all_images goods "$goods_item_id" "/api/goods/${goods_item_id}" || true
goods_delete_item_id=""
create_item_without_image goods_delete_item_id goods || true
verify_item_delete_clears_links goods "$goods_delete_item_id" "/api/goods/${goods_delete_item_id}" || true

create_item_without_image performance_item_id performance || true
verify_add_and_remove_all_images performance "$performance_item_id" "/api/performances/${performance_item_id}" || true
performance_delete_item_id=""
create_item_without_image performance_delete_item_id performance || true
verify_item_delete_clears_links performance "$performance_delete_item_id" "/api/performances/${performance_delete_item_id}" || true

echo "[INFO] e2e summary: passes=${PASSES}, failures=${FAILURES}"
if [[ "$FAILURES" -gt 0 ]]; then
  exit 1
fi
