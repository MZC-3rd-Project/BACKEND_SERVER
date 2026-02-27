#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOG_DIR="${LOG_DIR:-/tmp/product_thumbnail_webp_transition_logs_$(date +%Y%m%d_%H%M%S)}"
mkdir -p "$LOG_DIR"

PRODUCT_PORT="${PRODUCT_PORT:-18484}"
MEDIA_PORT="${MEDIA_PORT:-18494}"
WORKER_PORT="${WORKER_PORT:-18495}"
SELLER_ID="${SELLER_ID:-910001}"
STORE_ID="${STORE_ID:-920001}"

AWS_PROFILE_NAME="${AWS_PROFILE:-mzc}"
AWS_REGION_NAME="${AWS_REGION:-ap-northeast-2}"
MEDIA_BUCKET="${MEDIA_BUCKET:-team2-donmoa-media-raw}"
MEDIA_PREFIX="${MEDIA_PREFIX:-team2-donmoa-media}"
CF_COMMENT="${CF_DISTRIBUTION_COMMENT:-team2-donmoa-media}"
MEDIA_CLOUDFRONT_DOMAIN="${MEDIA_CLOUDFRONT_DOMAIN:-}"

PIDS=()
MEDIA_PID=""
WORKER_PID=""
PRODUCT_PID=""
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
  local body="${4:-}"
  if [[ "$actual" == "$expected" ]]; then
    pass "$label"
    return 0
  fi
  fail "$label (expected=${expected}, actual=${actual})"
  if [[ -n "$body" ]]; then
    echo "[DEBUG] ${label} response body: ${body}"
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

start_worker() {
  echo "[INFO] starting media-worker on ${WORKER_PORT}"
  (
    cd "$ROOT"
    env \
      AWS_PROFILE="$AWS_PROFILE_NAME" \
      AWS_REGION="$AWS_REGION_NAME" \
      GRADLE_USER_HOME=/tmp/.gradle-codex \
      SERVER_PORT="$WORKER_PORT" \
      SNOWFLAKE_WORKER_ID=25 \
      APP_GATEWAY_SECURITY_ENABLED=false \
      MEDIA_S3_REGION="$AWS_REGION_NAME" \
      MEDIA_S3_BUCKET="$MEDIA_BUCKET" \
      MEDIA_S3_KEY_PREFIX="$MEDIA_PREFIX" \
      MEDIA_CLOUDFRONT_DOMAIN="$MEDIA_DOMAIN" \
      MEDIA_WORKER_TASK_DISPATCH_FIXED_DELAY_MS=15000 \
      MEDIA_WORKER_TASK_STALE_RECOVERY_FIXED_DELAY_MS=30000 \
      MEDIA_WORKER_TASK_STALE_PROCESSING_SECONDS=30 \
      ./gradlew :servers:services:media-worker:bootRun --no-daemon >>"$LOG_DIR/media-worker.log" 2>&1
  ) &
  WORKER_PID="$!"
  PIDS+=("$WORKER_PID")
}

start_product() {
  echo "[INFO] starting product-service on ${PRODUCT_PORT}"
  (
    cd "$ROOT"
    env \
      GRADLE_USER_HOME=/tmp/.gradle-codex \
      SERVER_PORT="$PRODUCT_PORT" \
      SNOWFLAKE_WORKER_ID=26 \
      APP_GATEWAY_SECURITY_ENABLED=false \
      MEDIA_SERVICE_URL="http://127.0.0.1:${MEDIA_PORT}" \
      APP_MEDIA_LINK_SYNC_RETRY_FIXED_DELAY_MS=1000 \
      APP_MEDIA_LINK_SYNC_RETRY_MAX_RETRY_COUNT=3 \
      APP_MEDIA_LINK_SYNC_RETRY_BASE_DELAY_SECONDS=1 \
      APP_MEDIA_LINK_SYNC_RETRY_MAX_DELAY_SECONDS=2 \
      APP_MEDIA_LINK_SYNC_RETRY_STALE_SECONDS=10 \
      ./gradlew :servers:services:product:bootRun --no-daemon >>"$LOG_DIR/product.log" 2>&1
  ) &
  PRODUCT_PID="$!"
  PIDS+=("$PRODUCT_PID")
}

reset_data() {
  echo "[INFO] resetting media/product data"
  psql_exec media_db "TRUNCATE TABLE media_derivative_task_dlq, media_derivative_tasks, media_derivatives, media_links, media_files, processed_events, dead_letter_messages, outbox_messages RESTART IDENTITY CASCADE;"
  psql_exec product_db "TRUNCATE TABLE item_images, item_goods_links, item_options, shipping_infos, cast_members, seat_grades, performances, item_status_histories, item_media_link_sync_tasks, items, processed_events, dead_letter_messages, outbox_messages RESTART IDENTITY CASCADE;"
  pass "reset media/product data complete"
}

create_media_and_confirm() {
  local result_var="$1"
  local label="$2"
  local tmp_file
  tmp_file="$(mktemp)"
  local png_base64
  png_base64='iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/aWQAAAAASUVORK5CYII='
  printf '%s' "$png_base64" | openssl base64 -d -A >"$tmp_file"
  local file_size
  file_size="$(wc -c <"$tmp_file" | tr -d ' ')"

  local intent_raw intent_body intent_status
  intent_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${MEDIA_PORT}/api/v1/media/upload-intents" \
    -H "Content-Type: application/json" \
    -d "{\"fileName\":\"${label}.png\",\"contentType\":\"image/png\",\"fileSize\":${file_size}}")"
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
    -H "Content-Type: image/png" \
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

create_product_with_thumbnail() {
  local thumbnail_media_id="$1"
  local item_var="$2"
  if [[ -z "$thumbnail_media_id" ]]; then
    fail "product create thumbnailMediaId missing"
    return 1
  fi
  local title
  title="webp-transition-$(date +%s)"

  local payload raw body status item_id
  payload="$(cat <<JSON
{
  "title": "${title}",
  "description": "product thumbnail webp transition verify",
  "price": 21000,
  "storeId": ${STORE_ID},
  "thumbnailMediaId": ${thumbnail_media_id},
  "options": [
    {"optionName":"기본","additionalPrice":0,"stockQuantity":10}
  ],
  "shippingInfo": {
    "shippingFee": 3000,
    "freeShippingThreshold": 30000,
    "estimatedDays": 2,
    "returnPolicy": "7일 내 환불"
  }
}
JSON
)"

  raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${PRODUCT_PORT}/api/products" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: ${SELLER_ID}" \
    -d "$payload")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "product create status" "$status" "200" "$body" || return 1
  ensure_json_success "product create success" "$body" || return 1
  item_id="$(echo "$body" | jq -r '.data.id // empty')"
  if [[ -z "$item_id" || ! "$item_id" =~ ^[0-9]+$ ]]; then
    fail "product create itemId missing"
    echo "[DEBUG] product create response body: ${body}"
    return 1
  fi
  pass "product create itemId resolved (${item_id})"
  printf -v "$item_var" '%s' "$item_id"
}

wait_thumbnail_link_ready() {
  local item_id="$1"
  local media_id="$2"
  local timeout_seconds="$3"
  local elapsed=0
  while [[ "$elapsed" -lt "$timeout_seconds" ]]; do
    local cnt
    cnt="$(psql_query media_db "SELECT count(*) FROM media_links WHERE owner_type='ITEM' AND owner_id=${item_id} AND media_id=${media_id} AND usage_type='THUMBNAIL' AND deleted_at IS NULL;")"
    if [[ "$cnt" -ge 1 ]]; then
      pass "thumbnail media link synced (itemId=${item_id}, mediaId=${media_id})"
      return 0
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done
  fail "thumbnail media link sync timeout (itemId=${item_id}, mediaId=${media_id})"
  return 1
}

query_media_object_key() {
  local media_id="$1"
  local body_var="$2"
  local key_var="$3"
  local raw body status key
  raw="$(curl -sS -w '\n%{http_code}' "http://127.0.0.1:${MEDIA_PORT}/internal/v1/media/${media_id}/url")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "media internal url status (mediaId=${media_id})" "$status" "200" || return 1
  ensure_json_success "media internal url success (mediaId=${media_id})" "$body" || return 1
  key="$(echo "$body" | jq -r '.data.objectKey // empty')"
  if [[ -z "$key" ]]; then
    fail "media internal objectKey missing (mediaId=${media_id})"
    return 1
  fi
  printf -v "$body_var" '%s' "$body"
  printf -v "$key_var" '%s' "$key"
}

wait_derivative_ready() {
  local media_id="$1"
  local timeout_seconds="$2"
  local elapsed=0
  while [[ "$elapsed" -lt "$timeout_seconds" ]]; do
    local task_status derived_key
    task_status="$(psql_query media_db "SELECT COALESCE(status, '') FROM media_derivative_tasks WHERE media_id=${media_id} ORDER BY created_at DESC LIMIT 1;")"
    derived_key="$(psql_query media_db "SELECT COALESCE(object_key, '') FROM media_derivatives WHERE media_id=${media_id} AND derivative_profile='THUMBNAIL_WEBP' AND status='READY' ORDER BY media_version DESC, created_at DESC LIMIT 1;")"
    if [[ "$task_status" == "COMPLETED" && -n "$derived_key" ]]; then
      pass "media derivative completed (mediaId=${media_id})"
      return 0
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done
  fail "media derivative completion timeout (mediaId=${media_id})"
  return 1
}

require_cmd curl
require_cmd jq
require_cmd docker
require_cmd lsof
require_cmd openssl
require_cmd mktemp
require_cmd wc

check_port_free "$PRODUCT_PORT"
check_port_free "$MEDIA_PORT"
check_port_free "$WORKER_PORT"

echo "[INFO] preparing infra"
(cd "$ROOT/docker" && docker compose up -d postgres redis zookeeper kafka mariadb >/dev/null)

MEDIA_DOMAIN="$(resolve_media_domain)"
echo "[INFO] media public domain: ${MEDIA_DOMAIN}"

start_media
start_product

wait_health "media-api" "$MEDIA_PORT"
wait_health "product-service" "$PRODUCT_PORT"

reset_data

created_media_id=""
created_item_id=""
create_media_and_confirm created_media_id "product-thumbnail-webp"
create_product_with_thumbnail "$created_media_id" created_item_id
wait_thumbnail_link_ready "$created_item_id" "$created_media_id" 30

raw_body=""
raw_key=""
query_media_object_key "$created_media_id" raw_body raw_key
if [[ "$raw_key" == *"/raw/"* && "$raw_key" != *.webp ]]; then
  pass "before worker completion, media url points to raw objectKey"
else
  fail "before worker completion should be raw (actual=${raw_key})"
fi

start_worker
wait_health "media-worker" "$WORKER_PORT"

wait_derivative_ready "$created_media_id" 120

derived_body=""
derived_key=""
query_media_object_key "$created_media_id" derived_body derived_key
if [[ "$derived_key" == *"/derived/"* && "$derived_key" == *.webp ]]; then
  pass "after worker completion, media url points to derived webp objectKey"
else
  fail "after worker completion should be derived webp (actual=${derived_key})"
fi

echo "[INFO] summary: passes=${PASSES}, failures=${FAILURES}"
if [[ "$FAILURES" -gt 0 ]]; then
  exit 1
fi

pass "product thumbnail raw->webp transition verify passed"
