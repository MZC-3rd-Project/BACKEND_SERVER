#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

OUT_DIR="${OUT_DIR:-/tmp/media_progressive_delivery_measure_$(date +%Y%m%d_%H%M%S)}"
LOG_DIR="${OUT_DIR}/logs"
mkdir -p "$OUT_DIR" "$LOG_DIR"

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

INPUT_IMAGE="${INPUT_IMAGE:-docs/assets/sample-images/sample-large-1920x1080.png}"
ITERATIONS="${ITERATIONS:-5}"
FETCH_REPEATS="${FETCH_REPEATS:-3}"
POLL_INTERVAL_MS="${POLL_INTERVAL_MS:-100}"
TRANSITION_TIMEOUT_MS="${TRANSITION_TIMEOUT_MS:-120000}"
LINK_SYNC_TIMEOUT_SECONDS="${LINK_SYNC_TIMEOUT_SECONDS:-30}"

WORKER_DISPATCH_DELAY_MS="${WORKER_DISPATCH_DELAY_MS:-3000}"
WORKER_STALE_RECOVERY_DELAY_MS="${WORKER_STALE_RECOVERY_DELAY_MS:-30000}"
WORKER_STALE_SECONDS="${WORKER_STALE_SECONDS:-180}"
WORKER_THUMBNAIL_MAX_WIDTH="${WORKER_THUMBNAIL_MAX_WIDTH:-640}"
WORKER_THUMBNAIL_MAX_HEIGHT="${WORKER_THUMBNAIL_MAX_HEIGHT:-640}"
WORKER_WEBP_QUALITY="${WORKER_WEBP_QUALITY:-0.82}"

POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-project03-postgres}"

MEDIA_API_PID=""
MEDIA_WORKER_PID=""
PRODUCT_PID=""
MEDIA_API_JAR=""
MEDIA_WORKER_JAR=""
PRODUCT_JAR=""

info() {
  echo "[INFO] $*"
}

fail() {
  echo "[FAIL] $*" >&2
  exit 1
}

require_cmd() {
  local cmd="$1"
  command -v "$cmd" >/dev/null 2>&1 || fail "required command not found: ${cmd}"
}

check_port_free() {
  local port="$1"
  if lsof -tiTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    fail "port ${port} is already in use"
  fi
}

cleanup() {
  local pids=("${MEDIA_API_PID}" "${MEDIA_WORKER_PID}" "${PRODUCT_PID}")
  for pid in "${pids[@]}"; do
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
      wait "$pid" 2>/dev/null || true
    fi
  done
}
trap cleanup EXIT

sleep_ms() {
  local ms="$1"
  python3 - "$ms" <<'PY'
import sys
import time

ms = int(sys.argv[1])
time.sleep(max(0, ms) / 1000.0)
PY
}

now_ms() {
  python3 - <<'PY'
import time
print(int(time.time() * 1000))
PY
}

extract_http_body() {
  local raw="$1"
  printf '%s\n' "$raw" | sed '$d'
}

extract_http_status() {
  local raw="$1"
  printf '%s\n' "$raw" | tail -n1
}

ensure_http_ok() {
  local label="$1"
  local status="$2"
  local body="${3:-}"
  if [[ "$status" == "200" ]]; then
    return 0
  fi
  fail "${label} failed (status=${status}, body=${body})"
}

ensure_json_success() {
  local label="$1"
  local body="$2"
  local success
  success="$(echo "$body" | jq -r '.success // false')"
  if [[ "$success" != "true" ]]; then
    local code message
    code="$(echo "$body" | jq -r '.error.code // "UNKNOWN"')"
    message="$(echo "$body" | jq -r '.error.message // ""')"
    fail "${label} failed (code=${code}, message=${message})"
  fi
}

wait_health() {
  local name="$1"
  local port="$2"
  local url="http://127.0.0.1:${port}/actuator/health"
  local start now
  start="$(date +%s)"
  while true; do
    if curl -fsS --max-time 2 "$url" >/dev/null 2>&1; then
      return 0
    fi
    now="$(date +%s)"
    if (( now - start >= 240 )); then
      fail "${name} health timeout (port=${port})"
    fi
    sleep 2
  done
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

build_jars() {
  info "building media-api/media-worker/product bootJar"
  ./gradlew \
    :servers:services:media-api:bootJar \
    :servers:services:media-worker:bootJar \
    :servers:services:product:bootJar \
    --no-daemon >/dev/null

  MEDIA_API_JAR="$(find servers/services/media-api/build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*plain*' | head -n1)"
  MEDIA_WORKER_JAR="$(find servers/services/media-worker/build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*plain*' | head -n1)"
  PRODUCT_JAR="$(find servers/services/product/build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*plain*' | head -n1)"

  [[ -n "$MEDIA_API_JAR" ]] || fail "media-api bootJar not found"
  [[ -n "$MEDIA_WORKER_JAR" ]] || fail "media-worker bootJar not found"
  [[ -n "$PRODUCT_JAR" ]] || fail "product bootJar not found"
}

start_media_api() {
  info "starting media-api on ${MEDIA_PORT}"
  AWS_PROFILE="$AWS_PROFILE_NAME" \
  AWS_REGION="$AWS_REGION_NAME" \
  SERVER_PORT="$MEDIA_PORT" \
  APP_GATEWAY_SECURITY_ENABLED=false \
  MEDIA_S3_REGION="$AWS_REGION_NAME" \
  MEDIA_S3_BUCKET="$MEDIA_BUCKET" \
  MEDIA_S3_KEY_PREFIX="$MEDIA_PREFIX" \
  MEDIA_CLOUDFRONT_DOMAIN="$MEDIA_DOMAIN" \
  java -jar "$MEDIA_API_JAR" >"$LOG_DIR/media-api.log" 2>&1 &
  MEDIA_API_PID="$!"
  wait_health "media-api" "$MEDIA_PORT"
}

start_media_worker() {
  info "starting media-worker on ${WORKER_PORT}"
  AWS_PROFILE="$AWS_PROFILE_NAME" \
  AWS_REGION="$AWS_REGION_NAME" \
  SERVER_PORT="$WORKER_PORT" \
  SNOWFLAKE_WORKER_ID=21 \
  APP_GATEWAY_SECURITY_ENABLED=false \
  MEDIA_S3_REGION="$AWS_REGION_NAME" \
  MEDIA_S3_BUCKET="$MEDIA_BUCKET" \
  MEDIA_S3_KEY_PREFIX="$MEDIA_PREFIX" \
  MEDIA_CLOUDFRONT_DOMAIN="$MEDIA_DOMAIN" \
  MEDIA_WORKER_DERIVATIVE_THUMBNAIL_MAX_WIDTH="$WORKER_THUMBNAIL_MAX_WIDTH" \
  MEDIA_WORKER_DERIVATIVE_THUMBNAIL_MAX_HEIGHT="$WORKER_THUMBNAIL_MAX_HEIGHT" \
  MEDIA_WORKER_DERIVATIVE_WEBP_QUALITY="$WORKER_WEBP_QUALITY" \
  MEDIA_WORKER_TASK_DISPATCH_FIXED_DELAY_MS="$WORKER_DISPATCH_DELAY_MS" \
  MEDIA_WORKER_TASK_STALE_RECOVERY_FIXED_DELAY_MS="$WORKER_STALE_RECOVERY_DELAY_MS" \
  MEDIA_WORKER_TASK_STALE_PROCESSING_SECONDS="$WORKER_STALE_SECONDS" \
  java -jar "$MEDIA_WORKER_JAR" >"$LOG_DIR/media-worker.log" 2>&1 &
  MEDIA_WORKER_PID="$!"
  wait_health "media-worker" "$WORKER_PORT"
}

start_product() {
  info "starting product-service on ${PRODUCT_PORT}"
  SERVER_PORT="$PRODUCT_PORT" \
  SNOWFLAKE_WORKER_ID=22 \
  APP_GATEWAY_SECURITY_ENABLED=false \
  MEDIA_SERVICE_URL="http://127.0.0.1:${MEDIA_PORT}" \
  APP_MEDIA_LINK_SYNC_RETRY_FIXED_DELAY_MS=1000 \
  APP_MEDIA_LINK_SYNC_RETRY_MAX_RETRY_COUNT=3 \
  APP_MEDIA_LINK_SYNC_RETRY_BASE_DELAY_SECONDS=1 \
  APP_MEDIA_LINK_SYNC_RETRY_MAX_DELAY_SECONDS=2 \
  APP_MEDIA_LINK_SYNC_RETRY_STALE_SECONDS=10 \
  java -jar "$PRODUCT_JAR" >"$LOG_DIR/product.log" 2>&1 &
  PRODUCT_PID="$!"
  wait_health "product-service" "$PRODUCT_PORT"
}

reset_data() {
  info "resetting media/product tables"
  psql_exec media_db "TRUNCATE TABLE media_derivative_task_dlq, media_derivative_tasks, media_derivatives, media_links, media_files, processed_events, dead_letter_messages, outbox_messages RESTART IDENTITY CASCADE;"
  psql_exec product_db "TRUNCATE TABLE item_images, item_goods_links, item_options, shipping_infos, cast_members, seat_grades, performances, item_status_histories, item_media_link_sync_tasks, items, processed_events, dead_letter_messages, outbox_messages RESTART IDENTITY CASCADE;"
}

content_type_for_file() {
  local file_path="$1"
  case "${file_path##*.}" in
    png|PNG) echo "image/png" ;;
    jpg|JPG|jpeg|JPEG) echo "image/jpeg" ;;
    webp|WEBP) echo "image/webp" ;;
    *) echo "application/octet-stream" ;;
  esac
}

create_media_and_confirm_with_file() {
  local file_path="$1"
  local label="$2"
  local media_id_var="$3"
  local confirm_ts_var="$4"

  local file_size content_type file_name
  file_size="$(wc -c <"$file_path" | tr -d ' ')"
  content_type="$(content_type_for_file "$file_path")"
  file_name="$(basename "$file_path")"

  local intent_raw intent_body intent_status
  intent_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${MEDIA_PORT}/api/v1/media/upload-intents" \
    -H "Content-Type: application/json" \
    -d "{\"fileName\":\"${label}-${file_name}\",\"contentType\":\"${content_type}\",\"fileSize\":${file_size}}")"
  intent_body="$(extract_http_body "$intent_raw")"
  intent_status="$(extract_http_status "$intent_raw")"
  ensure_http_ok "upload-intent" "$intent_status" "$intent_body"
  ensure_json_success "upload-intent" "$intent_body"

  local created_media_id presigned_url upload_token
  created_media_id="$(echo "$intent_body" | jq -r '.data.mediaId // empty')"
  presigned_url="$(echo "$intent_body" | jq -r '.data.presignedUrl // empty')"
  upload_token="$(echo "$intent_body" | jq -r '.data.uploadToken // empty')"
  [[ -n "$created_media_id" ]] || fail "upload-intent missing mediaId"
  [[ -n "$presigned_url" ]] || fail "upload-intent missing presignedUrl"
  [[ -n "$upload_token" ]] || fail "upload-intent missing uploadToken"

  local put_status
  put_status="$(curl -sS -o /dev/null -w '%{http_code}' -X PUT "$presigned_url" \
    -H "Content-Type: ${content_type}" \
    --data-binary @"$file_path")"
  if [[ "$put_status" != "200" && "$put_status" != "204" ]]; then
    fail "presigned upload failed (status=${put_status})"
  fi

  local confirm_raw confirm_body confirm_status
  confirm_raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${MEDIA_PORT}/api/v1/media/confirm" \
    -H "Content-Type: application/json" \
    -d "{\"mediaId\":${created_media_id},\"uploadToken\":\"${upload_token}\"}")"
  confirm_body="$(extract_http_body "$confirm_raw")"
  confirm_status="$(extract_http_status "$confirm_raw")"
  ensure_http_ok "confirm" "$confirm_status" "$confirm_body"
  ensure_json_success "confirm" "$confirm_body"

  printf -v "$media_id_var" '%s' "$created_media_id"
  printf -v "$confirm_ts_var" '%s' "$(now_ms)"
}

create_product_with_thumbnail() {
  local thumbnail_media_id="$1"
  local item_id_var="$2"
  local title payload raw body status created_item_id

  [[ "$thumbnail_media_id" =~ ^[0-9]+$ ]] || fail "invalid thumbnail_media_id: ${thumbnail_media_id}"
  [[ "$STORE_ID" =~ ^[0-9]+$ ]] || fail "invalid STORE_ID: ${STORE_ID}"

  title="media-bench-$(now_ms)-${RANDOM}"
  payload="$(cat <<JSON
{
  "title": "${title}",
  "description": "media progressive benchmark",
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

  if ! echo "$payload" | jq -e . >/dev/null 2>&1; then
    fail "invalid product-create payload: ${payload}"
  fi

  raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${PRODUCT_PORT}/api/products" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: ${SELLER_ID}" \
    -d "$payload")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  ensure_http_ok "product-create" "$status" "$body"
  ensure_json_success "product-create" "$body"

  created_item_id="$(echo "$body" | jq -r '.data.id // empty')"
  [[ -n "$created_item_id" ]] || fail "product create missing item id"
  printf -v "$item_id_var" '%s' "$created_item_id"
}

wait_thumbnail_link_ready() {
  local item_id="$1"
  local media_id="$2"
  local elapsed=0
  while (( elapsed < LINK_SYNC_TIMEOUT_SECONDS )); do
    local cnt
    cnt="$(psql_query media_db "SELECT count(*) FROM media_links WHERE owner_type='ITEM' AND owner_id=${item_id} AND media_id=${media_id} AND usage_type='THUMBNAIL' AND deleted_at IS NULL;")"
    if [[ "$cnt" -ge 1 ]]; then
      return 0
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done
  fail "thumbnail media link sync timeout (itemId=${item_id}, mediaId=${media_id})"
}

fetch_media_url_snapshot() {
  local media_id="$1"
  local raw body status
  raw="$(curl -sS -w '\n%{http_code}' "http://127.0.0.1:${MEDIA_PORT}/internal/v1/media/${media_id}/url" || true)"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"
  if [[ "$status" != "200" ]]; then
    return 1
  fi
  local ok key url
  ok="$(echo "$body" | jq -r '.success // false' 2>/dev/null || echo false)"
  if [[ "$ok" != "true" ]]; then
    return 1
  fi
  key="$(echo "$body" | jq -r '.data.objectKey // empty')"
  url="$(echo "$body" | jq -r '.data.mediaUrl // empty')"
  if [[ -z "$key" || -z "$url" ]]; then
    return 1
  fi
  printf '%s|%s\n' "$key" "$url"
}

measure_transition() {
  local media_id="$1"
  local confirm_ts_ms="$2"
  local raw_latency_var="$3"
  local derived_latency_var="$4"
  local raw_url_var="$5"
  local derived_url_var="$6"
  local raw_key_var="$7"
  local derived_key_var="$8"

  local deadline raw_seen derived_seen
  deadline=$((confirm_ts_ms + TRANSITION_TIMEOUT_MS))
  raw_seen=0
  derived_seen=0

  local observed_raw_latency="-1"
  local observed_derived_latency="-1"
  local observed_raw_url=""
  local observed_derived_url=""
  local observed_raw_key=""
  local observed_derived_key=""

  while true; do
    local now_ms
    now_ms="$(now_ms)"
    if (( now_ms >= deadline )); then
      break
    fi

    local snapshot
    snapshot="$(fetch_media_url_snapshot "$media_id" || true)"
    if [[ -n "$snapshot" ]]; then
      local key url
      key="${snapshot%%|*}"
      url="${snapshot#*|}"

      if (( raw_seen == 0 )) && [[ "$key" == *"/raw/"* ]] && [[ "$key" != *.webp ]]; then
        raw_seen=1
        observed_raw_latency=$((now_ms - confirm_ts_ms))
        observed_raw_url="$url"
        observed_raw_key="$key"
      fi

      if [[ "$key" == *"/derived/"* ]] && [[ "$key" == *.webp ]]; then
        if (( raw_seen == 0 )); then
          raw_seen=1
          observed_raw_latency=$((now_ms - confirm_ts_ms))
          observed_raw_url="$url"
          observed_raw_key="$key"
        fi
        derived_seen=1
        observed_derived_latency=$((now_ms - confirm_ts_ms))
        observed_derived_url="$url"
        observed_derived_key="$key"
        break
      fi
    fi

    sleep_ms "$POLL_INTERVAL_MS"
  done

  if (( derived_seen == 0 )); then
    fail "derived switch timeout (mediaId=${media_id})"
  fi

  printf -v "$raw_latency_var" '%s' "$observed_raw_latency"
  printf -v "$derived_latency_var" '%s' "$observed_derived_latency"
  printf -v "$raw_url_var" '%s' "$observed_raw_url"
  printf -v "$derived_url_var" '%s' "$observed_derived_url"
  printf -v "$raw_key_var" '%s' "$observed_raw_key"
  printf -v "$derived_key_var" '%s' "$observed_derived_key"
}

append_cache_bust() {
  local url="$1"
  local token="$2"
  if [[ "$url" == *\?* ]]; then
    printf '%s&bench=%s\n' "$url" "$token"
  else
    printf '%s?bench=%s\n' "$url" "$token"
  fi
}

measure_fetch_once() {
  local iteration="$1"
  local phase="$2"
  local repeat="$3"
  local url="$4"
  local csv_file="$5"

  local bust_url out
  bust_url="$(append_cache_bust "$url" "${iteration}-${phase}-${repeat}-$(now_ms)-${RANDOM}")"
  out="$(curl -sS -L -o /dev/null -w '%{http_code},%{size_download},%{time_starttransfer},%{time_total}' "$bust_url" || true)"

  local http_code size_download ttfb_sec total_sec
  IFS=',' read -r http_code size_download ttfb_sec total_sec <<<"$out"
  if [[ -z "$http_code" ]]; then
    http_code="000"
    size_download="0"
    ttfb_sec="0"
    total_sec="0"
  fi
  printf '%s,%s,%s,%s,%s,%s,%s\n' \
    "$iteration" "$phase" "$repeat" "$http_code" "$size_download" "$ttfb_sec" "$total_sec" >>"$csv_file"
}

aggregate_and_write() {
  local latencies_csv="$1"
  local fetch_csv="$2"
  local summary_json="$3"
  local summary_md="$4"

  python3 - "$latencies_csv" "$fetch_csv" "$summary_json" "$INPUT_IMAGE" "$ITERATIONS" "$FETCH_REPEATS" "$POLL_INTERVAL_MS" "$TRANSITION_TIMEOUT_MS" "$WORKER_THUMBNAIL_MAX_WIDTH" "$WORKER_THUMBNAIL_MAX_HEIGHT" "$WORKER_WEBP_QUALITY" <<'PY'
import csv
import json
import math
import os
import statistics
import sys

latencies_csv, fetch_csv, summary_json, input_image, iterations, fetch_repeats, poll_ms, timeout_ms, thumbnail_max_width, thumbnail_max_height, webp_quality = sys.argv[1:]

def p95(values):
    if not values:
        return None
    sorted_vals = sorted(values)
    idx = max(0, math.ceil(len(sorted_vals) * 0.95) - 1)
    return sorted_vals[idx]

def stats(values):
    if not values:
        return {
            "count": 0,
            "min": None,
            "max": None,
            "avg": None,
            "p95": None,
        }
    return {
        "count": len(values),
        "min": round(min(values), 3),
        "max": round(max(values), 3),
        "avg": round(statistics.mean(values), 3),
        "p95": round(p95(values), 3),
    }

raw_first_ms = []
derived_switch_ms = []
transition_gap_ms = []

with open(latencies_csv, newline="") as f:
    reader = csv.DictReader(f)
    for row in reader:
        raw = float(row["raw_first_ms"])
        derived = float(row["derived_switch_ms"])
        raw_first_ms.append(raw)
        derived_switch_ms.append(derived)
        transition_gap_ms.append(derived - raw)

fetch_metrics = {"raw": {"size": [], "ttfb_ms": [], "total_ms": [], "http_codes": []},
                 "derived": {"size": [], "ttfb_ms": [], "total_ms": [], "http_codes": []}}

with open(fetch_csv, newline="") as f:
    reader = csv.DictReader(f)
    for row in reader:
        phase = row["phase"]
        if phase not in fetch_metrics:
            continue
        fetch_metrics[phase]["http_codes"].append(row["http_code"])
        fetch_metrics[phase]["size"].append(float(row["size_bytes"]))
        fetch_metrics[phase]["ttfb_ms"].append(float(row["ttfb_sec"]) * 1000.0)
        fetch_metrics[phase]["total_ms"].append(float(row["total_sec"]) * 1000.0)

raw_size_avg = statistics.mean(fetch_metrics["raw"]["size"]) if fetch_metrics["raw"]["size"] else 0.0
derived_size_avg = statistics.mean(fetch_metrics["derived"]["size"]) if fetch_metrics["derived"]["size"] else 0.0
if raw_size_avg > 0:
    size_reduction_percent = ((raw_size_avg - derived_size_avg) / raw_size_avg) * 100.0
else:
    size_reduction_percent = 0.0

raw_p95 = p95(raw_first_ms) or 0.0
derived_p95 = p95(derived_switch_ms) or 0.0
if derived_p95 > 0:
    wait_reduction_percent = ((derived_p95 - raw_p95) / derived_p95) * 100.0
else:
    wait_reduction_percent = 0.0

summary = {
    "scenario": {
        "inputImage": input_image,
        "inputImageBytes": os.path.getsize(input_image),
        "iterations": int(iterations),
        "fetchRepeats": int(fetch_repeats),
        "pollIntervalMs": int(poll_ms),
        "transitionTimeoutMs": int(timeout_ms),
        "workerThumbnailMaxWidth": int(thumbnail_max_width),
        "workerThumbnailMaxHeight": int(thumbnail_max_height),
        "workerWebpQuality": float(webp_quality),
    },
    "latency": {
        "rawFirstVisibleMs": stats(raw_first_ms),
        "derivedSwitchMs": stats(derived_switch_ms),
        "backgroundOptimizationGapMs": stats(transition_gap_ms),
    },
    "download": {
        "raw": {
            "sizeBytes": stats(fetch_metrics["raw"]["size"]),
            "ttfbMs": stats(fetch_metrics["raw"]["ttfb_ms"]),
            "totalMs": stats(fetch_metrics["raw"]["total_ms"]),
            "httpCodeSet": sorted(set(fetch_metrics["raw"]["http_codes"])),
        },
        "derived": {
            "sizeBytes": stats(fetch_metrics["derived"]["size"]),
            "ttfbMs": stats(fetch_metrics["derived"]["ttfb_ms"]),
            "totalMs": stats(fetch_metrics["derived"]["total_ms"]),
            "httpCodeSet": sorted(set(fetch_metrics["derived"]["http_codes"])),
        },
        "delta": {
            "sizeReductionPercent": round(size_reduction_percent, 2),
            "ttfbP95DeltaMs": round((stats(fetch_metrics["derived"]["ttfb_ms"])["p95"] or 0) - (stats(fetch_metrics["raw"]["ttfb_ms"])["p95"] or 0), 3),
            "totalP95DeltaMs": round((stats(fetch_metrics["derived"]["total_ms"])["p95"] or 0) - (stats(fetch_metrics["raw"]["total_ms"])["p95"] or 0), 3),
        },
    },
    "uxImpact": {
        "syncEquivalentWaitP95Ms": round(derived_p95, 3),
        "progressiveWaitP95Ms": round(raw_p95, 3),
        "waitReductionPercent": round(wait_reduction_percent, 2),
    },
    "artifacts": {
        "latenciesCsv": latencies_csv,
        "fetchCsv": fetch_csv,
    },
}

with open(summary_json, "w", encoding="utf-8") as f:
    json.dump(summary, f, ensure_ascii=False, indent=2)
PY

  {
    echo "# Media Progressive Delivery Measurement"
    echo
    echo "- input image: ${INPUT_IMAGE}"
    echo "- image bytes: $(wc -c <"$INPUT_IMAGE" | tr -d ' ')"
    echo "- iterations: ${ITERATIONS}"
    echo "- fetch repeats per phase: ${FETCH_REPEATS}"
    echo "- worker thumbnail max: ${WORKER_THUMBNAIL_MAX_WIDTH}x${WORKER_THUMBNAIL_MAX_HEIGHT}"
    echo "- worker webp quality: ${WORKER_WEBP_QUALITY}"
    echo
    echo "| metric | value |"
    echo "|---|---:|"
    echo "| raw first visible p95 (ms) | $(jq -r '.latency.rawFirstVisibleMs.p95' "$summary_json") |"
    echo "| derived switch p95 (ms) | $(jq -r '.latency.derivedSwitchMs.p95' "$summary_json") |"
    echo "| progressive wait reduction (%) | $(jq -r '.uxImpact.waitReductionPercent' "$summary_json") |"
    echo "| raw download total p95 (ms) | $(jq -r '.download.raw.totalMs.p95' "$summary_json") |"
    echo "| derived download total p95 (ms) | $(jq -r '.download.derived.totalMs.p95' "$summary_json") |"
    echo "| average size reduction (%) | $(jq -r '.download.delta.sizeReductionPercent' "$summary_json") |"
    echo
    echo "artifacts:"
    echo "- ${summary_json}"
    echo "- ${latencies_csv}"
    echo "- ${fetch_csv}"
    echo "- ${LOG_DIR}/media-api.log"
    echo "- ${LOG_DIR}/media-worker.log"
    echo "- ${LOG_DIR}/product.log"
  } >"$summary_md"
}

main() {
  require_cmd docker
  require_cmd curl
  require_cmd jq
  require_cmd aws
  require_cmd lsof
  require_cmd python3

  [[ -f "$INPUT_IMAGE" ]] || fail "input image not found: ${INPUT_IMAGE}"
  [[ "$ITERATIONS" =~ ^[0-9]+$ ]] || fail "ITERATIONS must be integer"
  [[ "$FETCH_REPEATS" =~ ^[0-9]+$ ]] || fail "FETCH_REPEATS must be integer"
  (( ITERATIONS > 0 )) || fail "ITERATIONS must be > 0"
  (( FETCH_REPEATS > 0 )) || fail "FETCH_REPEATS must be > 0"

  check_port_free "$PRODUCT_PORT"
  check_port_free "$MEDIA_PORT"
  check_port_free "$WORKER_PORT"

  info "starting infra"
  docker compose -f docker/docker-compose.yml up -d postgres redis zookeeper kafka mariadb >/dev/null

  MEDIA_DOMAIN="$(resolve_media_domain)"
  info "media domain: ${MEDIA_DOMAIN}"

  build_jars
  start_media_api
  start_media_worker
  start_product

  reset_data

  local latencies_csv fetch_csv summary_json summary_md
  latencies_csv="${OUT_DIR}/latencies.csv"
  fetch_csv="${OUT_DIR}/fetch_metrics.csv"
  summary_json="${OUT_DIR}/summary.json"
  summary_md="${OUT_DIR}/summary.md"

  echo "iteration,media_id,item_id,raw_first_ms,derived_switch_ms,transition_gap_ms,raw_key,derived_key" >"$latencies_csv"
  echo "iteration,phase,repeat,http_code,size_bytes,ttfb_sec,total_sec" >"$fetch_csv"

  local i
  for i in $(seq 1 "$ITERATIONS"); do
    info "iteration ${i}/${ITERATIONS}"

    local media_id confirm_ts_ms item_id
    create_media_and_confirm_with_file "$INPUT_IMAGE" "iter-${i}" media_id confirm_ts_ms
    create_product_with_thumbnail "$media_id" item_id
    wait_thumbnail_link_ready "$item_id" "$media_id"

    local raw_first_ms derived_switch_ms raw_url_out derived_url_out raw_key_out derived_key_out
    measure_transition "$media_id" "$confirm_ts_ms" raw_first_ms derived_switch_ms raw_url_out derived_url_out raw_key_out derived_key_out

    local transition_gap_ms
    transition_gap_ms=$((derived_switch_ms - raw_first_ms))
    printf '%s,%s,%s,%s,%s,%s,%s,%s\n' \
      "$i" "$media_id" "$item_id" "$raw_first_ms" "$derived_switch_ms" "$transition_gap_ms" "$raw_key_out" "$derived_key_out" >>"$latencies_csv"

    local r
    for r in $(seq 1 "$FETCH_REPEATS"); do
      measure_fetch_once "$i" "raw" "$r" "$raw_url_out" "$fetch_csv"
      measure_fetch_once "$i" "derived" "$r" "$derived_url_out" "$fetch_csv"
    done
  done

  aggregate_and_write "$latencies_csv" "$fetch_csv" "$summary_json" "$summary_md"

  info "done. out_dir=${OUT_DIR}"
  info "summary_json=${summary_json}"
  info "summary_md=${summary_md}"
}

main "$@"
