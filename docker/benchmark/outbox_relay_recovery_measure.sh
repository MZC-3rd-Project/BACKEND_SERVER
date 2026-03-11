#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

OUT_DIR="${OUT_DIR:-/tmp/outbox_relay_recovery_measure_$(date +%Y%m%d_%H%M%S)}"
LOG_DIR="$OUT_DIR/logs"
mkdir -p "$OUT_DIR" "$LOG_DIR"

PRODUCT_PORT="${PRODUCT_PORT:-18484}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-project03-postgres}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-project03-kafka}"
OUTAGE_BATCH_SIZE="${OUTAGE_BATCH_SIZE:-60}"
OUTAGE_OBSERVE_SECONDS="${OUTAGE_OBSERVE_SECONDS:-60}"
RECOVERY_TIMEOUT_SECONDS="${RECOVERY_TIMEOUT_SECONDS:-600}"

SELLER_ID="${SELLER_ID:-640001}"
STORE_ID="${STORE_ID:-650001}"

PRODUCT_PID=""
PRODUCT_JAR=""
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
  if [[ -n "${PRODUCT_PID}" ]] && kill -0 "${PRODUCT_PID}" 2>/dev/null; then
    kill "${PRODUCT_PID}" 2>/dev/null || true
    wait "${PRODUCT_PID}" 2>/dev/null || true
  fi
}
trap cleanup EXIT

require_cmd() {
  local cmd="$1"
  command -v "$cmd" >/dev/null 2>&1 || {
    echo "[ERR] command not found: $cmd" >&2
    exit 1
  }
}

wait_http_ok() {
  local url="$1"
  local timeout_seconds="${2:-180}"
  local start now
  start="$(date +%s)"
  while true; do
    if curl -fsS "$url" >/dev/null 2>&1; then
      return 0
    fi
    now="$(date +%s)"
    if (( now - start >= timeout_seconds )); then
      return 1
    fi
    sleep 1
  done
}

wait_kafka_ready() {
  for _ in {1..120}; do
    if docker exec -i "$KAFKA_CONTAINER" kafka-topics --bootstrap-server localhost:9092 --list >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
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

build_product_jar() {
  echo "[INFO] building product bootJar"
  ./gradlew :servers:services:product:bootJar --no-daemon >/dev/null
  PRODUCT_JAR="$(find servers/services/product/build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*plain*' | head -n1)"
  [[ -n "$PRODUCT_JAR" ]] || {
    echo "[ERR] product bootJar not found" >&2
    exit 1
  }
  pass "product bootJar ready (${PRODUCT_JAR})"
}

start_product() {
  echo "[INFO] starting product-service on ${PRODUCT_PORT}"
  SERVER_PORT="${PRODUCT_PORT}" \
  APP_GATEWAY_SECURITY_ENABLED=false \
  APP_OUTBOX_RELAY_FIXED_DELAY_MS=1000 \
  APP_OUTBOX_RELAY_FETCH_BEFORE_SECONDS=1 \
  APP_OUTBOX_RELAY_MAX_IN_FLIGHT=8 \
  APP_OUTBOX_RELAY_SENDING_STALE_THRESHOLD_SECONDS=15 \
  java -jar "$PRODUCT_JAR" >"$LOG_DIR/product.log" 2>&1 &
  PRODUCT_PID="$!"

  wait_http_ok "http://127.0.0.1:${PRODUCT_PORT}/actuator/health" 240 \
    && pass "product-service health UP" \
    || {
      fail "product-service health timeout"
      tail -n 120 "$LOG_DIR/product.log" || true
      exit 1
    }
}

psql_query() {
  local db="$1"
  local sql="$2"
  docker exec -i "$POSTGRES_CONTAINER" psql -U postgres -d "$db" -At -c "$sql"
}

create_product() {
  local label="$1"
  local ts payload raw body status item_id
  ts="$(date +%s%3N)"

  payload="$(cat <<JSON
{"title":"OutboxMeasure ${label} ${ts}","description":"outbox relay recovery measure","price":9900,"storeId":${STORE_ID},"options":[{"optionName":"기본","additionalPrice":0,"stockQuantity":3}],"shippingInfo":{"shippingFee":2500,"freeShippingThreshold":30000,"estimatedDays":2,"returnPolicy":"returnable"}}
JSON
)"

  raw="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${PRODUCT_PORT}/api/products" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: ${SELLER_ID}" \
    -d "$payload")"
  body="$(extract_http_body "$raw")"
  status="$(extract_http_status "$raw")"

  [[ "$status" == "200" ]] || {
    echo "[ERR] create product failed (${label}) status=${status} body=${body}" >&2
    return 1
  }
  item_id="$(echo "$body" | jq -r '.data.id // empty')"
  [[ -n "$item_id" && "$item_id" != "null" ]] || {
    echo "[ERR] missing item id (${label}) body=${body}" >&2
    return 1
  }
  echo "$item_id"
}

count_outbox_status_for_ids() {
  local ids_csv="$1"
  local status="$2"
  psql_query product_db \
    "SELECT COUNT(*) FROM outbox_messages WHERE aggregate_type='Item' AND event_type='ITEM_CREATED' AND aggregate_id IN (${ids_csv}) AND status='${status}';" \
    | tr -d '[:space:]'
}

count_outbox_non_published_for_ids() {
  local ids_csv="$1"
  psql_query product_db \
    "SELECT COUNT(*) FROM outbox_messages WHERE aggregate_type='Item' AND event_type='ITEM_CREATED' AND aggregate_id IN (${ids_csv}) AND status <> 'PUBLISHED';" \
    | tr -d '[:space:]'
}

main() {
  require_cmd docker
  require_cmd curl
  require_cmd jq
  require_cmd python3

  echo "[INFO] starting infra"
  docker compose -f docker/docker-compose.yml up -d postgres redis zookeeper kafka >/dev/null
  pass "docker infra up"
  wait_kafka_ready && pass "kafka ready" || {
    fail "kafka ready timeout"
    exit 1
  }

  build_product_jar
  start_product

  local baseline_id baseline_published
  baseline_id="$(create_product "baseline")"
  baseline_published="$(count_outbox_status_for_ids "'${baseline_id}'" "PUBLISHED")"
  [[ "$baseline_published" == "1" ]] && pass "baseline item published" || fail "baseline item not published"

  echo "[INFO] injecting kafka outage"
  docker compose -f docker/docker-compose.yml stop kafka >/dev/null
  pass "kafka stopped"

  local ids_file="$OUT_DIR/outage_item_ids.txt"
  : >"$ids_file"
  for i in $(seq 1 "$OUTAGE_BATCH_SIZE"); do
    create_product "outage-${i}" >>"$ids_file"
  done
  pass "outage batch created (${OUTAGE_BATCH_SIZE})"

  local ids_csv
  ids_csv="$(awk '{printf sep "'\''%s'\''", $0; sep=","}' "$ids_file")"
  [[ -n "$ids_csv" ]] || {
    fail "failed to build ids csv"
    exit 1
  }

  local non_published_during_outage published_during_outage
  published_during_outage="$(count_outbox_status_for_ids "$ids_csv" "PUBLISHED")"
  non_published_during_outage="$(count_outbox_non_published_for_ids "$ids_csv")"
  echo "[INFO] outage observed published=${published_during_outage}, nonPublished=${non_published_during_outage}"

  local observe_start observe_now
  observe_start="$(date +%s)"
  while true; do
    observe_now="$(date +%s)"
    if (( observe_now - observe_start >= OUTAGE_OBSERVE_SECONDS )); then
      break
    fi
    sleep 1
  done

  local non_published_before_recovery
  non_published_before_recovery="$(count_outbox_non_published_for_ids "$ids_csv")"

  echo "[INFO] recovering kafka"
  docker compose -f docker/docker-compose.yml up -d kafka >/dev/null
  if wait_kafka_ready; then
    pass "kafka recovered"
  else
    echo "[WARN] kafka recover timeout. retrying with zookeeper+kafka restart"
    docker compose -f docker/docker-compose.yml restart zookeeper >/dev/null
    docker compose -f docker/docker-compose.yml up -d kafka >/dev/null
    wait_kafka_ready && pass "kafka recovered (restart path)" || {
      fail "kafka recover timeout"
      exit 1
    }
  fi

  local recovery_start_ms recovery_deadline published_now non_published_now
  recovery_start_ms="$(python3 - <<'PY'
import time
print(int(time.time()*1000))
PY
)"
  recovery_deadline=$(( $(date +%s) + RECOVERY_TIMEOUT_SECONDS ))
  while true; do
    non_published_now="$(count_outbox_non_published_for_ids "$ids_csv")"
    published_now="$(count_outbox_status_for_ids "$ids_csv" "PUBLISHED")"
    if [[ "$published_now" -eq "$OUTAGE_BATCH_SIZE" && "$non_published_now" -eq 0 ]]; then
      break
    fi
    if (( $(date +%s) >= recovery_deadline )); then
      break
    fi
    sleep 1
  done
  local recovery_end_ms
  recovery_end_ms="$(python3 - <<'PY'
import time
print(int(time.time()*1000))
PY
)"

  local published_final non_published_final
  published_final="$(count_outbox_status_for_ids "$ids_csv" "PUBLISHED")"
  non_published_final="$(count_outbox_non_published_for_ids "$ids_csv")"

  local final_success_rate retry_success_rate drain_ms drain_sec
  final_success_rate="$(python3 - <<PY
total=${OUTAGE_BATCH_SIZE}
published=${published_final}
print(f"{(published/total)*100:.2f}")
PY
)"
  retry_success_rate="$(python3 - <<PY
before=${non_published_before_recovery}
after=${non_published_final}
if before <= 0:
    print("100.00")
else:
    recovered=before-after
    if recovered < 0:
        recovered=0
    print(f"{(recovered/before)*100:.2f}")
PY
)"
  drain_ms="$(( recovery_end_ms - recovery_start_ms ))"
  drain_sec="$(python3 - <<PY
print(f"{${drain_ms}/1000:.2f}")
PY
)"

  local summary_json="$OUT_DIR/summary.json"
  cat >"$summary_json" <<JSON
{
  "scenario": {
    "outageBatchSize": ${OUTAGE_BATCH_SIZE},
    "outageObserveSeconds": ${OUTAGE_OBSERVE_SECONDS},
    "recoveryTimeoutSeconds": ${RECOVERY_TIMEOUT_SECONDS}
  },
  "metrics": {
    "publishedDuringOutage": ${published_during_outage},
    "nonPublishedBeforeRecovery": ${non_published_before_recovery},
    "publishedFinal": ${published_final},
    "nonPublishedFinal": ${non_published_final},
    "finalPublishSuccessRatePercent": ${final_success_rate},
    "retryRecoverySuccessRatePercent": ${retry_success_rate},
    "backlogDrainMsAfterRecovery": ${drain_ms},
    "backlogDrainSecondsAfterRecovery": ${drain_sec}
  }
}
JSON

  local summary_md="$OUT_DIR/summary.md"
  {
    echo "# Outbox Relay Recovery Measurement"
    echo
    echo "- outage batch size: ${OUTAGE_BATCH_SIZE}"
    echo "- outage observe window: ${OUTAGE_OBSERVE_SECONDS}s"
    echo "- recovery timeout: ${RECOVERY_TIMEOUT_SECONDS}s"
    echo
    echo "| metric | value |"
    echo "|---|---:|"
    echo "| final publish success rate (%) | ${final_success_rate} |"
    echo "| retry recovery success rate (%) | ${retry_success_rate} |"
    echo "| backlog drain after recovery (s) | ${drain_sec} |"
    echo "| non-published before recovery | ${non_published_before_recovery} |"
    echo "| non-published after recovery | ${non_published_final} |"
    echo
    echo "artifacts:"
    echo "- ${summary_json}"
    echo "- ${ids_file}"
    echo "- ${LOG_DIR}/product.log"
  } >"$summary_md"

  echo "[INFO] summary: passes=${PASSES}, failures=${FAILURES}"
  echo "[INFO] out_dir: ${OUT_DIR}"
  echo "[INFO] summary_json: ${summary_json}"
  echo "[INFO] summary_md: ${summary_md}"

  if (( FAILURES > 0 )); then
    exit 1
  fi
}

main "$@"
