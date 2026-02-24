#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOG_DIR="${LOG_DIR:-/tmp/media_client_validator_e2e_logs_$(date +%Y%m%d_%H%M%S)}"
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

ensure_json_failure() {
  local label="$1"
  local body="$2"
  local success
  success="$(echo "$body" | jq -r '.success // false')"
  if [[ "$success" == "false" ]]; then
    pass "$label"
    return 0
  fi
  fail "$label (expected failure payload)"
  return 1
}

ensure_error_code() {
  local label="$1"
  local body="$2"
  local expected="$3"
  local code
  code="$(echo "$body" | jq -r '.error.code // empty')"
  if [[ "$code" == "$expected" ]]; then
    pass "$label"
  else
    fail "$label (expected=${expected}, actual=${code})"
  fi
}

ensure_http_not_success() {
  local label="$1"
  local actual="$2"
  if [[ "$actual" != "200" ]]; then
    pass "$label"
  else
    fail "$label (actual=${actual})"
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

create_product_item() {
  local result_var="$1"
  local ts payload raw body status created_item_id
  ts="$(date +%s)"
  payload="$(cat <<JSON
{
  "title": "Validator E2E Product ${ts}",
  "description": "media-client validator e2e",
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

  raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${PRODUCT_PORT}/api/products" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: ${SELLER_ID}" \
    -d "$payload")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "product create status" "$status" "200"
  ensure_json_success "product create success" "$body" || return 1

  created_item_id="$(echo "$body" | jq -r '.data.id // empty')"
  if [[ -z "$created_item_id" ]]; then
    fail "product create itemId missing"
    return 1
  fi
  pass "product create itemId resolved (${created_item_id})"
  printf -v "$result_var" '%s' "$created_item_id"
}

create_media_and_confirm() {
  local result_var="$1"
  local label="$2"
  local tmp_file file_size intent_raw intent_body intent_status
  tmp_file="$(mktemp)"
  printf '%s' "${label}-$(date +%s%N)" >"$tmp_file"
  file_size="$(wc -c <"$tmp_file" | tr -d ' ')"

  intent_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${MEDIA_PORT}/api/v1/media/upload-intents" \
    -H "Content-Type: application/json" \
    -d "{\"fileName\":\"${label}.jpg\",\"contentType\":\"image/jpeg\",\"fileSize\":${file_size}}")"
  intent_body="$(extract_http_body "$intent_raw")"
  intent_status="$(extract_http_status "$intent_raw")"
  ensure_http_status "media intent status" "$intent_status" "200"
  ensure_json_success "media intent success" "$intent_body" || return 1

  local media_id presigned_url upload_token
  media_id="$(echo "$intent_body" | jq -r '.data.mediaId // empty')"
  presigned_url="$(echo "$intent_body" | jq -r '.data.presignedUrl // empty')"
  upload_token="$(echo "$intent_body" | jq -r '.data.uploadToken // empty')"

  local put_status
  put_status="$(curl -sS -o /dev/null -w '%{http_code}' -X PUT "$presigned_url" \
    -H "Content-Type: image/jpeg" \
    --data-binary @"$tmp_file")"
  if [[ "$put_status" == "200" || "$put_status" == "204" ]]; then
    pass "media presigned upload"
  else
    fail "media presigned upload (status=${put_status})"
    return 1
  fi

  local confirm_raw confirm_body confirm_status
  confirm_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${MEDIA_PORT}/api/v1/media/confirm" \
    -H "Content-Type: application/json" \
    -d "{\"mediaId\":${media_id},\"uploadToken\":\"${upload_token}\"}")"
  confirm_body="$(extract_http_body "$confirm_raw")"
  confirm_status="$(extract_http_status "$confirm_raw")"
  ensure_http_status "media confirm status" "$confirm_status" "200"
  ensure_json_success "media confirm success" "$confirm_body" || return 1

  rm -f "$tmp_file"
  printf -v "$result_var" '%s' "$media_id"
}

verify_invalid_media_reference() {
  local item_id="$1"
  local payload raw body status

  payload='[{"mediaId":0,"sortOrder":0,"isThumbnail":true}]'
  raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${PRODUCT_PORT}/api/items/${item_id}/images" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: ${SELLER_ID}" \
    -d "$payload")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_not_success "invalid mediaId=0 should fail" "$status"
  ensure_json_failure "invalid mediaId=0 failure payload" "$body"
  local code_zero
  code_zero="$(echo "$body" | jq -r '.error.code // empty')"
  if [[ "$code_zero" == "PRODUCT-602" || "$code_zero" == "SYS-001" ]]; then
    pass "invalid mediaId=0 code is expected fallback (${code_zero})"
  else
    fail "invalid mediaId=0 code mismatch (actual=${code_zero})"
  fi

  payload='[{"mediaId":999999999999,"sortOrder":0,"isThumbnail":true}]'
  raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${PRODUCT_PORT}/api/items/${item_id}/images" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: ${SELLER_ID}" \
    -d "$payload")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "unknown mediaId status" "$status" "400"
  ensure_json_failure "unknown mediaId failure payload" "$body"
  ensure_error_code "unknown mediaId code" "$body" "PRODUCT-602"
}

verify_valid_media_reference() {
  local item_id="$1"
  local media_id="$2"
  local payload raw body status added_count

  payload="[{\"mediaId\":${media_id},\"sortOrder\":0,\"isThumbnail\":true}]"
  raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${PRODUCT_PORT}/api/items/${item_id}/images" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: ${SELLER_ID}" \
    -d "$payload")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_status "valid media add status" "$status" "200"
  ensure_json_success "valid media add success" "$body" || return 1

  added_count="$(echo "$body" | jq -r '(.data // []) | length')"
  if [[ "$added_count" == "1" ]]; then
    pass "valid media add count is 1"
  else
    fail "valid media add count is 1 (actual=${added_count})"
  fi
}

require_cmd curl
require_cmd jq
require_cmd docker
require_cmd lsof

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

item_id=""
create_product_item item_id
verify_invalid_media_reference "$item_id"
valid_media_id=""
create_media_and_confirm valid_media_id validator-e2e
verify_valid_media_reference "$item_id" "$valid_media_id"

echo "[INFO] e2e summary: passes=${PASSES}, failures=${FAILURES}"
if [[ "$FAILURES" -gt 0 ]]; then
  exit 1
fi
