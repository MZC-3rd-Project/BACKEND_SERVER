#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOG_DIR="${LOG_DIR:-/tmp/search_enricher_dlq_logs_$(date +%Y%m%d_%H%M%S)}"
mkdir -p "$LOG_DIR"

SEARCH_PORT="${SEARCH_PORT:-18388}"
PRODUCT_PORT="${PRODUCT_PORT:-18384}"
MEDIA_PORT="${MEDIA_PORT:-18394}"
WORKER_PORT="${WORKER_PORT:-18395}"
ELASTICSEARCH_PORT="${ELASTICSEARCH_PORT:-23173}"
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
PRODUCT_PID=""
SEARCH_PID=""
WORKER_PID=""
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

wait_es_up() {
  for _ in {1..90}; do
    local code
    code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 2 "http://127.0.0.1:${ELASTICSEARCH_PORT}/_cluster/health" 2>/dev/null || true)"
    if [[ "$code" == "200" ]]; then
      pass "elasticsearch is reachable"
      return 0
    fi
    sleep 2
  done
  fail "elasticsearch reachability timeout (port=${ELASTICSEARCH_PORT})"
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

reset_search_consumer_offsets() {
  docker exec -i project03-kafka kafka-consumer-groups \
    --bootstrap-server localhost:9092 \
    --group search-service-group \
    --topic item-events \
    --reset-offsets --to-latest --execute >/dev/null || true
  pass "search consumer offset reset to latest for item-events"
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

start_product() {
  echo "[INFO] starting product-service on ${PRODUCT_PORT}"
  (
    cd "$ROOT"
    env \
      GRADLE_USER_HOME=/tmp/.gradle-codex \
      SERVER_PORT="$PRODUCT_PORT" \
      SNOWFLAKE_WORKER_ID=13 \
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

start_worker() {
  echo "[INFO] starting media-worker on ${WORKER_PORT}"
  (
    cd "$ROOT"
    env \
      AWS_PROFILE="$AWS_PROFILE_NAME" \
      AWS_REGION="$AWS_REGION_NAME" \
      GRADLE_USER_HOME=/tmp/.gradle-codex \
      SERVER_PORT="$WORKER_PORT" \
      SNOWFLAKE_WORKER_ID=15 \
      APP_GATEWAY_SECURITY_ENABLED=false \
      MEDIA_S3_REGION="$AWS_REGION_NAME" \
      MEDIA_S3_BUCKET="$MEDIA_BUCKET" \
      MEDIA_S3_KEY_PREFIX="$MEDIA_PREFIX" \
      MEDIA_CLOUDFRONT_DOMAIN="$MEDIA_DOMAIN" \
      MEDIA_WORKER_TASK_DISPATCH_FIXED_DELAY_MS=500 \
      MEDIA_WORKER_TASK_STALE_RECOVERY_FIXED_DELAY_MS=2000 \
      MEDIA_WORKER_TASK_STALE_PROCESSING_SECONDS=10 \
      ./gradlew :servers:services:media-worker:bootRun --no-daemon >>"$LOG_DIR/media-worker.log" 2>&1
  ) &
  WORKER_PID="$!"
  PIDS+=("$WORKER_PID")
}

start_search() {
  echo "[INFO] starting search-service on ${SEARCH_PORT}"
  (
    cd "$ROOT"
    env \
      GRADLE_USER_HOME=/tmp/.gradle-codex \
      SERVER_PORT="$SEARCH_PORT" \
      SNOWFLAKE_WORKER_ID=14 \
      APP_GATEWAY_SECURITY_ENABLED=false \
      MEDIA_SERVICE_URL="http://127.0.0.1:${MEDIA_PORT}" \
      ELASTICSEARCH_URIS="http://127.0.0.1:${ELASTICSEARCH_PORT}" \
      SEARCH_THUMBNAIL_ENRICHER_ENABLED=true \
      SEARCH_THUMBNAIL_ENRICHER_FIXED_DELAY_MS=1000 \
      SEARCH_THUMBNAIL_ENRICHER_BATCH_SIZE=20 \
      SEARCH_THUMBNAIL_ENRICHER_MAX_RETRY_COUNT=8 \
      SEARCH_THUMBNAIL_ENRICHER_BASE_DELAY_SECONDS=1 \
      SEARCH_THUMBNAIL_ENRICHER_MAX_DELAY_SECONDS=3 \
      SEARCH_THUMBNAIL_ENRICHER_PROCESSING_STALE_THRESHOLD_SECONDS=10 \
      APP_KAFKA_CONSUMER_MAX_RETRY_ATTEMPTS=2 \
      APP_KAFKA_CONSUMER_INITIAL_BACKOFF_MS=200 \
      APP_KAFKA_CONSUMER_BACKOFF_MULTIPLIER=1 \
      APP_KAFKA_CONSUMER_MAX_BACKOFF_MS=400 \
      ./gradlew :servers:services:search:bootRun --no-daemon >>"$LOG_DIR/search.log" 2>&1
  ) &
  SEARCH_PID="$!"
  PIDS+=("$SEARCH_PID")
}

recreate_search_index() {
  local attempt max_attempts raw body status success code message
  max_attempts=10

  for attempt in $(seq 1 "$max_attempts"); do
    raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${SEARCH_PORT}/internal/v1/search/indexes/recreate?indexName=items")"
    body="$(extract_http_body "$raw")"
    status="$(extract_http_status "$raw")"
    success="$(echo "$body" | jq -r '.success // false' 2>/dev/null || true)"

    if [[ "$status" == "200" && "$success" == "true" ]]; then
      pass "search index recreate status"
      pass "search index recreate success"
      if [[ "$attempt" -gt 1 ]]; then
        echo "[INFO] search index recreate succeeded after retry (attempt=${attempt})"
      fi
      return 0
    fi

    if [[ "$attempt" -lt "$max_attempts" ]]; then
      echo "[WARN] search index recreate attempt ${attempt}/${max_attempts} failed (status=${status}), retrying..."
      sleep 2
    fi
  done

  code="$(echo "$body" | jq -r '.error.code // "UNKNOWN"' 2>/dev/null || true)"
  message="$(echo "$body" | jq -r '.error.message // ""' 2>/dev/null || true)"
  fail "search index recreate retry exhausted (status=${status}, code=${code}, message=${message})"
  return 1
}

reset_data() {
  echo "[INFO] resetting media/product/search data"
  psql_exec media_db "TRUNCATE TABLE media_derivative_task_dlq, media_derivative_tasks, media_derivatives, media_links, media_files, processed_events, dead_letter_messages, outbox_messages RESTART IDENTITY CASCADE;"
  psql_exec product_db "TRUNCATE TABLE item_images, item_goods_links, item_options, shipping_infos, cast_members, seat_grades, performances, item_status_histories, item_media_link_sync_tasks, items, processed_events, dead_letter_messages, outbox_messages RESTART IDENTITY CASCADE;"
  psql_exec search_db "TRUNCATE TABLE search_indexing_failures, search_thumbnail_enrichment_tasks, processed_events, dead_letter_messages, outbox_messages RESTART IDENTITY CASCADE;"
  pass "reset data complete"
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

wait_media_derivative_ready() {
  local media_id="$1"
  local timeout_seconds="$2"
  local elapsed=0
  while [[ "$elapsed" -lt "$timeout_seconds" ]]; do
    local task_status derived_key
    task_status="$(psql_query media_db "SELECT COALESCE(status, '') FROM media_derivative_tasks WHERE media_id=${media_id} ORDER BY created_at DESC LIMIT 1;")"
    derived_key="$(psql_query media_db "SELECT COALESCE(object_key, '') FROM media_derivatives WHERE media_id=${media_id} AND derivative_profile='THUMBNAIL_WEBP' AND status='READY' ORDER BY media_version DESC, created_at DESC LIMIT 1;")"

    if [[ "$task_status" == "COMPLETED" && -n "$derived_key" ]]; then
      pass "media derivative task completed (mediaId=${media_id})"
      if [[ "$derived_key" == *"/derived/"* && "$derived_key" == *.webp ]]; then
        pass "media derivative object key is webp derived path (mediaId=${media_id})"
        return 0
      fi
      fail "media derivative object key is invalid (mediaId=${media_id}, key=${derived_key})"
      return 1
    fi

    sleep 1
    elapsed=$((elapsed + 1))
  done
  fail "media derivative task timeout (mediaId=${media_id})"
  return 1
}

create_product_with_thumbnail() {
  local thumbnail_media_id="$1"
  local title_prefix="$2"
  local item_id_var="$3"
  local title_var="$4"

  local ts title payload raw body status item_id
  ts="$(date +%s)"
  title="${title_prefix}-${ts}"
  payload="$(cat <<JSON
{
  "title": "${title}",
  "description": "search enricher e2e",
  "price": 19000,
  "storeId": ${STORE_ID},
  "thumbnailMediaId": ${thumbnail_media_id},
  "options": [
    {"optionName":"기본","additionalPrice":0,"stockQuantity":12}
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
  ensure_http_status "${title_prefix} create status" "$status" "200" || return 1
  ensure_json_success "${title_prefix} create success" "$body" || return 1
  item_id="$(echo "$body" | jq -r '.data.id // empty')"
  if [[ -z "$item_id" ]]; then
    fail "${title_prefix} create itemId missing"
    return 1
  fi
  pass "${title_prefix} create itemId resolved (${item_id})"
  printf -v "$item_id_var" '%s' "$item_id"
  printf -v "$title_var" '%s' "$title"
}

wait_search_thumbnail_url() {
  local item_id="$1"
  local query="$2"
  local timeout_seconds="$3"
  local elapsed=0
  local attempt=0
  while [[ "$elapsed" -lt "$timeout_seconds" ]]; do
    local raw body status success thumb_url thumb_media page_size
    # Alternate page size during polling to avoid repeatedly reading a stale cache key.
    page_size=$((20 + (attempt % 2)))
    raw="$(curl -sS -w '\n%{http_code}' "http://127.0.0.1:${SEARCH_PORT}/api/v1/search?q=${query}&size=${page_size}")"
    body="$(extract_http_body "$raw")"
    status="$(extract_http_status "$raw")"
    success="$(echo "$body" | jq -r '.success // false')"
    if [[ "$status" == "200" && "$success" == "true" ]]; then
      thumb_url="$(echo "$body" | jq -r --arg id "$item_id" '.data.items[]? | select((.itemId|tostring)==$id) | .thumbnailUrl // empty' | head -n1)"
      thumb_media="$(echo "$body" | jq -r --arg id "$item_id" '.data.items[]? | select((.itemId|tostring)==$id) | .thumbnailMediaId // empty' | head -n1)"
      if [[ -n "$thumb_url" && -n "$thumb_media" ]]; then
        pass "search thumbnail snapshot ready (itemId=${item_id})"
        return 0
      fi
    fi
    sleep 2
    elapsed=$((elapsed + 2))
    attempt=$((attempt + 1))
  done
  fail "search thumbnail snapshot timeout (itemId=${item_id})"
  return 1
}

wait_search_thumbnail_url_contains() {
  local item_id="$1"
  local query="$2"
  local required_substring="$3"
  local timeout_seconds="$4"
  local elapsed=0
  local attempt=0
  while [[ "$elapsed" -lt "$timeout_seconds" ]]; do
    local raw body status success thumb_url page_size
    # Alternate page size during polling to avoid repeatedly reading a stale cache key.
    page_size=$((20 + (attempt % 2)))
    raw="$(curl -sS -w '\n%{http_code}' "http://127.0.0.1:${SEARCH_PORT}/api/v1/search?q=${query}&size=${page_size}")"
    body="$(extract_http_body "$raw")"
    status="$(extract_http_status "$raw")"
    success="$(echo "$body" | jq -r '.success // false')"
    if [[ "$status" == "200" && "$success" == "true" ]]; then
      thumb_url="$(echo "$body" | jq -r --arg id "$item_id" '.data.items[]? | select((.itemId|tostring)==$id) | .thumbnailUrl // empty' | head -n1)"
      if [[ -n "$thumb_url" && "$thumb_url" == *"$required_substring"* ]]; then
        pass "search thumbnail contains '${required_substring}' (itemId=${item_id})"
        return 0
      fi
    fi
    sleep 2
    elapsed=$((elapsed + 2))
    attempt=$((attempt + 1))
  done
  fail "search thumbnail missing '${required_substring}' (itemId=${item_id})"
  return 1
}

wait_search_task_status() {
  local item_id="$1"
  local target_status="$2"
  local timeout_seconds="$3"
  local elapsed=0
  while [[ "$elapsed" -lt "$timeout_seconds" ]]; do
    local status
    status="$(psql_query search_db "SELECT COALESCE(status, '') FROM search_thumbnail_enrichment_tasks WHERE item_id=${item_id};")"
    if [[ "$status" == "$target_status" ]]; then
      pass "search task status ${target_status} reached (itemId=${item_id})"
      return 0
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done
  local final_status
  final_status="$(psql_query search_db "SELECT COALESCE(status, '') FROM search_thumbnail_enrichment_tasks WHERE item_id=${item_id};")"
  fail "search task status ${target_status} timeout (itemId=${item_id}, final=${final_status})"
  return 1
}

wait_search_task_retry_observed() {
  local item_id="$1"
  local timeout_seconds="$2"
  local elapsed=0
  while [[ "$elapsed" -lt "$timeout_seconds" ]]; do
    local retry_count
    retry_count="$(psql_query search_db "SELECT COALESCE(retry_count, 0) FROM search_thumbnail_enrichment_tasks WHERE item_id=${item_id};")"
    if [[ -n "$retry_count" && "$retry_count" -ge 1 ]]; then
      pass "search task retry observed (itemId=${item_id}, retryCount=${retry_count})"
      return 0
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done
  fail "search task retry not observed (itemId=${item_id})"
  return 1
}

run_enricher_e2e_cases() {
  echo "[INFO] case1: search enricher happy path"
  local media1 item1 title1
  create_media_and_confirm media1 "search-e2e-m1" || return 1
  wait_media_derivative_ready "$media1" 120 || return 1
  create_product_with_thumbnail "$media1" "search-e2e-item1" item1 title1 || return 1
  wait_search_thumbnail_url "$item1" "$title1" 120 || return 1
  wait_search_thumbnail_url_contains "$item1" "$title1" "/derived/" 120 || return 1
  wait_search_thumbnail_url_contains "$item1" "$title1" ".webp" 120 || return 1
  wait_search_task_status "$item1" "COMPLETED" 120 || return 1

  echo "[INFO] case2: search enricher retry and recovery"
  local media2 item2 title2
  create_media_and_confirm media2 "search-e2e-m2" || return 1
  wait_media_derivative_ready "$media2" 120 || return 1
  create_product_with_thumbnail "$media2" "search-e2e-item2" item2 title2 || return 1

  stop_media
  pass "media-api stopped for enricher retry injection"
  wait_search_task_retry_observed "$item2" 30 || return 1

  start_media
  wait_health "media-api" "$MEDIA_PORT" || return 1
  wait_search_task_status "$item2" "COMPLETED" 120 || return 1
  wait_search_thumbnail_url "$item2" "$title2" 120 || return 1
}

run_dlq_case() {
  echo "[INFO] case3: search consumer DLQ on indexing failure"
  (cd "$ROOT/docker" && docker compose stop elasticsearch >/dev/null)
  pass "elasticsearch stopped for DLQ injection"

  local before_count
  before_count="$(psql_query search_db "SELECT count(*) FROM dead_letter_messages;")"

  local media_dlq item_dlq title_dlq
  create_media_and_confirm media_dlq "search-dlq-m1" || return 1
  create_product_with_thumbnail "$media_dlq" "search-dlq-item" item_dlq title_dlq || return 1
  pass "DLQ test item event published via product outbox (itemId=${item_dlq})"

  local elapsed=0 found=0
  while [[ "$elapsed" -lt 60 ]]; do
    local cnt
    cnt="$(psql_query search_db "SELECT count(*) FROM dead_letter_messages;")"
    if [[ "$cnt" -gt "$before_count" ]]; then
      found=1
      break
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done
  if [[ "$found" == "1" ]]; then
    pass "DLQ row created for search consumer failure"
  else
    fail "DLQ row not created"
    return 1
  fi

  (cd "$ROOT/docker" && docker compose up -d elasticsearch >/dev/null)
  wait_es_up || return 1
  recreate_search_index || return 1
}

require_cmd curl
require_cmd jq
require_cmd docker
require_cmd lsof
require_cmd mktemp
require_cmd openssl
require_cmd wc

check_port_free "$SEARCH_PORT"
check_port_free "$PRODUCT_PORT"
check_port_free "$MEDIA_PORT"
check_port_free "$WORKER_PORT"

echo "[INFO] preparing infra"
(cd "$ROOT/docker" && docker compose up -d postgres redis zookeeper kafka mariadb elasticsearch >/dev/null)
wait_es_up
reset_search_consumer_offsets

MEDIA_DOMAIN="$(resolve_media_domain)"
echo "[INFO] media public domain: ${MEDIA_DOMAIN}"

start_media
start_worker
start_product
start_search

wait_health "media-api" "$MEDIA_PORT"
wait_health "media-worker" "$WORKER_PORT"
wait_health "product-service" "$PRODUCT_PORT"
wait_health "search-service" "$SEARCH_PORT"

reset_data
recreate_search_index

run_enricher_e2e_cases
run_dlq_case

echo "[INFO] result passes=${PASSES} failures=${FAILURES}"
if [[ "$FAILURES" -gt 0 ]]; then
  exit 1
fi

pass "search enricher + DLQ verify passed"
