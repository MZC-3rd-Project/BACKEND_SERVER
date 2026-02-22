#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOG_DIR="${LOG_DIR:-/tmp/gateway_media_bff_e2e_logs_$(date +%Y%m%d_%H%M%S)}"
mkdir -p "$LOG_DIR"

GW_PORT="${GW_PORT:-18173}"
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

INTERNAL_AUTH_TOKEN="${INTERNAL_AUTH_TOKEN:-gw-bff-e2e-token}"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/.gradle-codex}"

PIDS=()
PASSES=0
FAILURES=0
JWT_TOKEN=""

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
  for _ in {1..150}; do
    local status
    status="$(curl -sS --max-time 2 "http://127.0.0.1:${port}/actuator/health" | jq -r '.status' 2>/dev/null || true)"
    if [[ "$status" == "UP" ]]; then
      pass "$name health is UP"
      return 0
    fi
    sleep 2
  done
  fail "$name health timeout"
  return 1
}

wait_gateway_ready() {
  local url="http://127.0.0.1:${GW_PORT}/bff/v1/products"
  for _ in {1..150}; do
    local code
    code="$(curl -sS -o /dev/null -w "%{http_code}" --max-time 2 -X POST "$url" \
      -H "Content-Type: application/json" \
      -d '{"item":{}}' || true)"
    if [[ "$code" == "401" ]]; then
      pass "client-gateway ready (BFF write is protected)"
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

b64url() {
  printf '%s' "$1" | openssl base64 -A | tr '+/' '-_' | tr -d '='
}

build_test_jwt() {
  local header payload
  header='{"alg":"none","typ":"JWT"}'
  payload="$(printf '{"userId":%s,"roles":["seller","user"]}' "$SELLER_ID")"
  JWT_TOKEN="$(b64url "$header").$(b64url "$payload")."
}

auth_header() {
  echo "Authorization: Bearer ${JWT_TOKEN}"
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

create_media_and_confirm_via_gateway() {
  local result_var="$1"
  local label="$2"
  local tmp_file file_size

  tmp_file="$(mktemp)"
  printf '%s' "${label}-$(date +%s%N)" >"$tmp_file"
  file_size="$(wc -c <"$tmp_file" | tr -d ' ')"

  local intent_raw intent_body intent_status
  intent_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${GW_PORT}/api/v1/media/upload-intents" \
    -H "Content-Type: application/json" \
    -H "$(auth_header)" \
    -d "{\"fileName\":\"${label}.jpg\",\"contentType\":\"image/jpeg\",\"fileSize\":${file_size}}")"
  intent_body="$(extract_http_body "$intent_raw")"
  intent_status="$(extract_http_status "$intent_raw")"
  ensure_http_status "${label} upload-intent via gateway status" "$intent_status" "200"
  ensure_json_success "${label} upload-intent via gateway success" "$intent_body" || return 1

  local media_id presigned_url upload_token
  media_id="$(echo "$intent_body" | jq -r '.data.mediaId // empty')"
  presigned_url="$(echo "$intent_body" | jq -r '.data.presignedUrl // empty')"
  upload_token="$(echo "$intent_body" | jq -r '.data.uploadToken // empty')"
  if [[ -z "$media_id" || -z "$presigned_url" || -z "$upload_token" ]]; then
    fail "${label} upload-intent response missing media info"
    rm -f "$tmp_file"
    return 1
  fi

  local put_status
  put_status="$(curl -sS -o /dev/null -w '%{http_code}' -X PUT "$presigned_url" \
    -H "Content-Type: image/jpeg" \
    --data-binary @"$tmp_file")"
  if [[ "$put_status" == "200" || "$put_status" == "204" ]]; then
    pass "${label} presigned upload"
  else
    fail "${label} presigned upload failed (status=${put_status})"
    rm -f "$tmp_file"
    return 1
  fi

  local confirm_raw confirm_body confirm_status
  confirm_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${GW_PORT}/api/v1/media/confirm" \
    -H "Content-Type: application/json" \
    -H "$(auth_header)" \
    -d "{\"mediaId\":${media_id},\"uploadToken\":\"${upload_token}\"}")"
  confirm_body="$(extract_http_body "$confirm_raw")"
  confirm_status="$(extract_http_status "$confirm_raw")"
  ensure_http_status "${label} confirm via gateway status" "$confirm_status" "200"
  ensure_json_success "${label} confirm via gateway success" "$confirm_body" || return 1

  rm -f "$tmp_file"
  printf -v "$result_var" '%s' "$media_id"
}

bff_create_item() {
  local kind="$1"
  local payload_json="$2"
  local images_json="$3"
  local result_var="$4"

  local raw body status
  raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${GW_PORT}/bff/v1/${kind}" \
    -H "Content-Type: application/json" \
    -H "$(auth_header)" \
    -d "{\"item\":${payload_json},\"images\":${images_json}}")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "bff create ${kind} status" "$status" "200"
  ensure_json_success "bff create ${kind} success" "$body" || return 1

  local item_id
  item_id="$(echo "$body" | jq -r '.data.id // empty')"
  if [[ -z "$item_id" ]]; then
    fail "bff create ${kind} itemId missing"
    return 1
  fi
  pass "bff create ${kind} itemId resolved (${item_id})"
  printf -v "$result_var" '%s' "$item_id"
}

bff_update_product() {
  local item_id="$1"
  local add_images_json="$2"
  local delete_ids_json="$3"
  local reorder_ids_json="$4"

  local raw body status
  raw="$(curl -sS -w '\n%{http_code}' -X PUT "http://127.0.0.1:${GW_PORT}/bff/v1/products/${item_id}" \
    -H "Content-Type: application/json" \
    -H "$(auth_header)" \
    -d "{\"item\":{},\"addImages\":${add_images_json},\"deleteImageIds\":${delete_ids_json},\"reorderImageIds\":${reorder_ids_json}}")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "bff update product(${item_id}) status" "$status" "200"
  ensure_json_success "bff update product(${item_id}) success" "$body" || return 1
}

bff_get_item_detail() {
  local type="$1"
  local item_id="$2"
  curl -sS "http://127.0.0.1:${GW_PORT}/bff/v1/items/${item_id}?type=${type}"
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

verify_gateway_protection() {
  local raw body status

  raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${GW_PORT}/api/v1/media/upload-intents" \
    -H "Content-Type: application/json" \
    -d '{"fileName":"unauth.jpg","contentType":"image/jpeg","fileSize":3}')"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "gateway media write unauthorized" "$status" "401"

  raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${GW_PORT}/bff/v1/products" \
    -H "Content-Type: application/json" \
    -d '{"item":{}}')"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "gateway bff write unauthorized" "$status" "401"
}

require_cmd curl
require_cmd jq
require_cmd docker
require_cmd lsof
require_cmd openssl

if [[ ! -f "$ROOT/docker/.env" ]]; then
  echo "[ERR] docker/.env not found at $ROOT/docker/.env" >&2
  exit 1
fi

SIGNING_KEY="$(sed -n 's/^APP_SECURITY_CONTEXT_SIGNING_KEY=//p' "$ROOT/docker/.env" | head -n1)"
MAX_AGE="$(sed -n 's/^APP_SECURITY_CONTEXT_MAX_AGE_MILLIS=//p' "$ROOT/docker/.env" | head -n1)"
if [[ -z "$SIGNING_KEY" ]]; then
  echo "[ERR] APP_SECURITY_CONTEXT_SIGNING_KEY is empty in docker/.env" >&2
  exit 1
fi
if [[ -z "$MAX_AGE" ]]; then
  MAX_AGE=300000
fi

build_test_jwt

check_port_free "$GW_PORT"
check_port_free "$PRODUCT_PORT"
check_port_free "$MEDIA_PORT"

echo "[INFO] preparing infra"
(cd "$ROOT/docker" && docker compose up -d >/dev/null)

MEDIA_DOMAIN="$(resolve_media_domain)"
echo "[INFO] media public domain: $MEDIA_DOMAIN"

echo "[INFO] starting media-api"
(
  cd "$ROOT"
  env \
    GRADLE_USER_HOME="$GRADLE_USER_HOME" \
    SERVER_PORT="$MEDIA_PORT" \
    AWS_PROFILE="$AWS_PROFILE_NAME" \
    AWS_REGION="$AWS_REGION_NAME" \
    APP_SECURITY_CONTEXT_SIGNING_KEY="$SIGNING_KEY" \
    APP_SECURITY_CONTEXT_MAX_AGE_MILLIS="$MAX_AGE" \
    APP_GATEWAY_SECURITY_ENABLED=true \
    APP_GATEWAY_SECURITY_INTERNAL_AUTH_TOKEN="$INTERNAL_AUTH_TOKEN" \
    MEDIA_S3_REGION="$AWS_REGION_NAME" \
    MEDIA_S3_BUCKET="$MEDIA_BUCKET" \
    MEDIA_S3_KEY_PREFIX="$MEDIA_PREFIX" \
    MEDIA_CLOUDFRONT_DOMAIN="$MEDIA_DOMAIN" \
    ./gradlew :servers:services:media-api:bootRun --no-daemon >"$LOG_DIR/media-api.log" 2>&1
) &
PIDS+=("$!")

echo "[INFO] starting product-service"
(
  cd "$ROOT"
  env \
    GRADLE_USER_HOME="$GRADLE_USER_HOME" \
    SERVER_PORT="$PRODUCT_PORT" \
    APP_SECURITY_CONTEXT_SIGNING_KEY="$SIGNING_KEY" \
    APP_SECURITY_CONTEXT_MAX_AGE_MILLIS="$MAX_AGE" \
    APP_GATEWAY_SECURITY_ENABLED=true \
    APP_GATEWAY_SECURITY_INTERNAL_AUTH_TOKEN="$INTERNAL_AUTH_TOKEN" \
    MEDIA_SERVICE_URL="http://127.0.0.1:${MEDIA_PORT}" \
    ./gradlew :servers:services:product:bootRun --no-daemon >"$LOG_DIR/product.log" 2>&1
) &
PIDS+=("$!")

echo "[INFO] starting client-gateway"
(
  cd "$ROOT"
  env \
    GRADLE_USER_HOME="$GRADLE_USER_HOME" \
    SERVER_PORT="$GW_PORT" \
    APP_SECURITY_CONTEXT_SIGNING_KEY="$SIGNING_KEY" \
    APP_SECURITY_CONTEXT_MAX_AGE_MILLIS="$MAX_AGE" \
    GATEWAY_SECURITY_INTERNAL_AUTH_TOKEN="$INTERNAL_AUTH_TOKEN" \
    APP_SERVICE_PRODUCT_URL="http://127.0.0.1:${PRODUCT_PORT}" \
    APP_SERVICE_MEDIA_URL="http://127.0.0.1:${MEDIA_PORT}" \
    ./gradlew :servers:gateways:client-gateway:bootRun --no-daemon >"$LOG_DIR/gateway.log" 2>&1
) &
PIDS+=("$!")

wait_health "media-api" "$MEDIA_PORT"
wait_health "product-service" "$PRODUCT_PORT"
wait_gateway_ready

echo "[INFO] resetting test data"
psql_exec media_db "TRUNCATE TABLE media_links, media_files, processed_events, dead_letter_messages, outbox_messages RESTART IDENTITY CASCADE;"
psql_exec product_db "TRUNCATE TABLE item_images, item_goods_links, item_options, shipping_infos, cast_members, seat_grades, performances, item_status_histories, items, processed_events, dead_letter_messages, outbox_messages RESTART IDENTITY CASCADE;"
pass "reset media/product test data"

verify_gateway_protection

ts="$(date +%s)"
product_payload="$(cat <<JSON
{"title":"GW BFF Product ${ts}","description":"gateway bff e2e product","price":12000,"storeId":${STORE_ID},"options":[{"optionName":"기본","additionalPrice":0,"stockQuantity":10}],"shippingInfo":{"shippingFee":3000,"freeShippingThreshold":50000,"estimatedDays":2,"returnPolicy":"7일 내 환불"}}
JSON
)"
goods_payload="$(cat <<JSON
{"title":"GW BFF Goods ${ts}","description":"gateway bff e2e goods","price":22000,"storeId":${STORE_ID},"options":[{"optionName":"M","additionalPrice":0,"stockQuantity":8}],"shippingInfo":{"shippingFee":2500,"freeShippingThreshold":40000,"estimatedDays":3,"returnPolicy":"교환/환불 가능"},"linkedPerformanceItemIds":[]}
JSON
)"
performance_payload="$(cat <<JSON
{"title":"GW BFF Performance ${ts}","description":"gateway bff e2e performance","price":55000,"storeId":${STORE_ID},"venue":"테스트홀","performanceDate":"2030-01-10","performanceTime":"19:30:00","totalSeats":120,"seatGrades":[{"gradeName":"R","price":65000,"totalQuantity":60,"fundingQuantity":10}],"castMembers":[{"name":"테스트 배우","role":"주연","profileImageUrl":"https://example.com/cast.jpg"}]}
JSON
)"

product_item_id=""
goods_item_id=""
performance_item_id=""

bff_create_item products "$product_payload" "[]" product_item_id
bff_create_item goods "$goods_payload" "[]" goods_item_id
bff_create_item performances "$performance_payload" "[]" performance_item_id

detail_product_empty="$(bff_get_item_detail PRODUCT "$product_item_id")"
ensure_json_success "bff product detail success after no-image create" "$detail_product_empty"
assert_images_empty "bff product detail empty images" "$detail_product_empty"

detail_goods_empty="$(bff_get_item_detail GOODS "$goods_item_id")"
ensure_json_success "bff goods detail success after no-image create" "$detail_goods_empty"
assert_images_empty "bff goods detail empty images" "$detail_goods_empty"

detail_perf_empty="$(bff_get_item_detail PERFORMANCE "$performance_item_id")"
ensure_json_success "bff performance detail success after no-image create" "$detail_perf_empty"
assert_images_empty "bff performance detail empty images" "$detail_perf_empty"

product_m1=""
product_m2=""
product_m3=""
product_m4=""
create_media_and_confirm_via_gateway product_m1 "product-m1"
create_media_and_confirm_via_gateway product_m2 "product-m2"
create_media_and_confirm_via_gateway product_m3 "product-m3"
create_media_and_confirm_via_gateway product_m4 "product-m4"

add_images_json="$(cat <<JSON
[{"mediaId":${product_m1},"sortOrder":0,"isThumbnail":true},{"mediaId":${product_m2},"sortOrder":1,"isThumbnail":false},{"mediaId":${product_m3},"sortOrder":2,"isThumbnail":false}]
JSON
)"
bff_update_product "$product_item_id" "$add_images_json" "[]" "[]"

detail_after_add="$(bff_get_item_detail PRODUCT "$product_item_id")"
ensure_json_success "bff product detail after add success" "$detail_after_add"

thumb_media_after_add="$(echo "$detail_after_add" | jq -r '.data.images.thumbnail.mediaId // empty')"
gallery_count_after_add="$(echo "$detail_after_add" | jq -r '(.data.images.gallery // []) | length')"
if [[ "$thumb_media_after_add" == "$product_m1" && "$gallery_count_after_add" == "2" ]]; then
  pass "bff product images added (thumbnail+gallery)"
else
  fail "bff product images added (thumbnail=${thumb_media_after_add}, galleryCount=${gallery_count_after_add})"
fi

thumb_image_id="$(echo "$detail_after_add" | jq -r '.data.images.thumbnail.id // empty')"
gallery_first_id="$(echo "$detail_after_add" | jq -r '.data.images.gallery[0].id // empty')"
gallery_second_id="$(echo "$detail_after_add" | jq -r '.data.images.gallery[1].id // empty')"

reorder_ids_json="$(cat <<JSON
[${thumb_image_id},${gallery_second_id},${gallery_first_id}]
JSON
)"
bff_update_product "$product_item_id" "[]" "[]" "$reorder_ids_json"

detail_after_reorder="$(bff_get_item_detail PRODUCT "$product_item_id")"
ensure_json_success "bff product detail after reorder success" "$detail_after_reorder"
gallery_first_media_after_reorder="$(echo "$detail_after_reorder" | jq -r '.data.images.gallery[0].mediaId // empty')"
gallery_second_media_after_reorder="$(echo "$detail_after_reorder" | jq -r '.data.images.gallery[1].mediaId // empty')"
if [[ "$gallery_first_media_after_reorder" == "$product_m3" && "$gallery_second_media_after_reorder" == "$product_m2" ]]; then
  pass "bff product reorder reflected in gallery order"
else
  fail "bff product reorder reflected (galleryMedia=${gallery_first_media_after_reorder},${gallery_second_media_after_reorder})"
fi

image_id_m2="$(echo "$detail_after_reorder" | jq -r --arg target "$product_m2" '.data.images.gallery[] | select((.mediaId|tostring) == $target) | .id' | head -n1)"
if [[ -z "$image_id_m2" || "$image_id_m2" == "null" ]]; then
  fail "bff product delete-one precondition failed: image id for media ${product_m2} not found"
  exit 1
fi
delete_m2_json="[${image_id_m2}]"
bff_update_product "$product_item_id" "[]" "$delete_m2_json" "[]"

detail_after_delete_one="$(bff_get_item_detail PRODUCT "$product_item_id")"
ensure_json_success "bff product detail after delete-one success" "$detail_after_delete_one"
gallery_count_after_delete_one="$(echo "$detail_after_delete_one" | jq -r '(.data.images.gallery // []) | length')"
if [[ "$gallery_count_after_delete_one" == "1" ]]; then
  pass "bff product delete-one applied"
else
  fail "bff product delete-one applied (galleryCount=${gallery_count_after_delete_one})"
fi

add_m4_json="$(cat <<JSON
[{"mediaId":${product_m4},"sortOrder":5,"isThumbnail":false}]
JSON
)"
bff_update_product "$product_item_id" "$add_m4_json" "[]" "[]"
detail_after_add_m4="$(bff_get_item_detail PRODUCT "$product_item_id")"
ensure_json_success "bff product detail after add m4 success" "$detail_after_add_m4"
gallery_count_after_add_m4="$(echo "$detail_after_add_m4" | jq -r '(.data.images.gallery // []) | length')"
if [[ "$gallery_count_after_add_m4" == "2" ]]; then
  pass "bff product add m4 applied"
else
  fail "bff product add m4 applied (galleryCount=${gallery_count_after_add_m4})"
fi

all_image_ids_json="$(echo "$detail_after_add_m4" | jq -c '[.data.images.thumbnail.id, (.data.images.gallery[]?.id)] | map(select(. != null))')"
bff_update_product "$product_item_id" "[]" "$all_image_ids_json" "[]"
detail_after_clear="$(bff_get_item_detail PRODUCT "$product_item_id")"
ensure_json_success "bff product detail after clear success" "$detail_after_clear"
assert_images_empty "bff product clear-all images empty" "$detail_after_clear"

active_item_images="$(psql_query product_db "SELECT count(*) FROM item_images WHERE item_id=${product_item_id} AND deleted_at IS NULL;")"
thumb_media_id_after_clear="$(psql_query product_db "SELECT COALESCE(thumbnail_media_id::text, '') FROM items WHERE id=${product_item_id};")"
active_media_links="$(psql_query media_db "SELECT count(*) FROM media_links WHERE owner_type='ITEM' AND owner_id=${product_item_id} AND deleted_at IS NULL;")"
if [[ "$active_item_images" == "0" ]]; then
  pass "product item_images active count after clear is 0"
else
  fail "product item_images active count after clear is 0 (actual=${active_item_images})"
fi
if [[ -z "$thumb_media_id_after_clear" ]]; then
  pass "product thumbnail_media_id cleared after clear-all"
else
  fail "product thumbnail_media_id cleared after clear-all (actual=${thumb_media_id_after_clear})"
fi
if [[ "$active_media_links" == "0" ]]; then
  pass "media_links active count after clear-all is 0"
else
  fail "media_links active count after clear-all is 0 (actual=${active_media_links})"
fi

g1=""
p1=""
create_media_and_confirm_via_gateway g1 "goods-m1"
create_media_and_confirm_via_gateway p1 "perf-m1"

goods_with_image_payload="$(cat <<JSON
[{"mediaId":${g1},"sortOrder":0,"isThumbnail":true}]
JSON
)"
performance_with_image_payload="$(cat <<JSON
[{"mediaId":${p1},"sortOrder":0,"isThumbnail":true}]
JSON
)"

goods_item_with_image_id=""
perf_item_with_image_id=""
bff_create_item goods "$goods_payload" "$goods_with_image_payload" goods_item_with_image_id
bff_create_item performances "$performance_payload" "$performance_with_image_payload" perf_item_with_image_id

detail_goods_image="$(bff_get_item_detail GOODS "$goods_item_with_image_id")"
ensure_json_success "bff goods detail with image success" "$detail_goods_image"
goods_thumb_media="$(echo "$detail_goods_image" | jq -r '.data.images.thumbnail.mediaId // empty')"
if [[ "$goods_thumb_media" == "$g1" ]]; then
  pass "bff goods thumbnail image reflected"
else
  fail "bff goods thumbnail image reflected (actual=${goods_thumb_media})"
fi

detail_perf_image="$(bff_get_item_detail PERFORMANCE "$perf_item_with_image_id")"
ensure_json_success "bff performance detail with image success" "$detail_perf_image"
perf_thumb_media="$(echo "$detail_perf_image" | jq -r '.data.images.thumbnail.mediaId // empty')"
if [[ "$perf_thumb_media" == "$p1" ]]; then
  pass "bff performance thumbnail image reflected"
else
  fail "bff performance thumbnail image reflected (actual=${perf_thumb_media})"
fi

for t in PRODUCT GOODS PERFORMANCE; do
  raw="$(curl -sS -w '\n%{http_code}' "http://127.0.0.1:${GW_PORT}/bff/v1/items?type=${t}&size=20")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "bff item list(${t}) status" "$status" "200"
  ensure_json_success "bff item list(${t}) success" "$body"
done

echo "[INFO] result passes=${PASSES} failures=${FAILURES}"
if [[ "$FAILURES" -gt 0 ]]; then
  exit 1
fi

pass "gateway media+bff e2e passed"
