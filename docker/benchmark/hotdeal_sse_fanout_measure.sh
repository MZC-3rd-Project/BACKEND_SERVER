#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

USERS="${USERS:-2000}"
HOTDEAL_ID="${HOTDEAL_ID:-990001}"
IMMEDIATE_MS="${IMMEDIATE_MS:-1000}"
TIMEOUT_MS="${TIMEOUT_MS:-12000}"
SEED="${SEED:-20260302}"
TRIGGER_MODE="${TRIGGER_MODE:-redis-publish}"

PORT1="${PORT1:-18089}"
PORT2="${PORT2:-18090}"
PORT3="${PORT3:-18091}"
BASES="http://127.0.0.1:${PORT1},http://127.0.0.1:${PORT2},http://127.0.0.1:${PORT3}"

OUT_DIR="${OUT_DIR:-/tmp/hotdeal_sse_fanout_measure_$(date +%Y%m%d_%H%M%S)}"
LOG_DIR="${OUT_DIR}/logs"
CLASS_DIR="${OUT_DIR}/classes"
mkdir -p "$OUT_DIR" "$LOG_DIR" "$CLASS_DIR"

HOTDEAL_PIDS=()
HOTDEAL_JAR=""
POSTGRES_HOST_PORT="${POSTGRES_HOST_PORT:-}"
CASE_TOPICS=""

cleanup() {
  for pid in "${HOTDEAL_PIDS[@]:-}"; do
    if [[ -n "${pid}" ]] && kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
      wait "$pid" 2>/dev/null || true
    fi
  done
}
trap cleanup EXIT

info() {
  echo "[INFO] $*"
}

fail() {
  echo "[FAIL] $*" >&2
  exit 1
}

wait_http_ok() {
  local url="$1"
  local timeout="${2:-180}"
  local start now
  start="$(date +%s)"
  while true; do
    if curl -fsS "$url" >/dev/null 2>&1; then
      return 0
    fi
    now="$(date +%s)"
    if (( now - start >= timeout )); then
      return 1
    fi
    sleep 2
  done
}

ensure_infra() {
  info "starting infra (postgres, redis, zookeeper, kafka)"
  docker compose -f docker/docker-compose.yml up -d postgres redis zookeeper kafka >/dev/null
  wait_http_ok "http://127.0.0.1:9411/health" 1 || true
  if [[ -z "$POSTGRES_HOST_PORT" ]]; then
    POSTGRES_HOST_PORT="$(docker port project03-postgres 5432/tcp | head -n1 | awk -F: '{print $NF}')"
  fi
  [[ -n "$POSTGRES_HOST_PORT" ]] || fail "failed to resolve postgres host port"
  info "resolved postgres host port: $POSTGRES_HOST_PORT"
}

clear_queue_keys() {
  local hotdeal_id="$1"
  info "clearing redis keys for hotDealId=${hotdeal_id}"
  docker exec project03-redis redis-cli DEL "hotdeal:queue:${hotdeal_id}" >/dev/null || true
  docker exec project03-redis sh -lc "redis-cli --scan --pattern 'hotdeal:token:${hotdeal_id}:*' | xargs -r redis-cli DEL >/dev/null" || true
  docker exec project03-redis sh -lc "redis-cli --scan --pattern 'hotdeal:admitted:${hotdeal_id}:*' | xargs -r redis-cli DEL >/dev/null" || true
}

start_hotdeal_instance() {
  local port="$1"
  local topic="$2"
  local worker="$3"
  local log_file="$4"

  SERVER_PORT="$port" \
  DB_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_HOST_PORT}/hotdeal_db" \
  DB_USERNAME="postgres" \
  DB_PASSWORD="postgres" \
  REDIS_HOST="127.0.0.1" \
  REDIS_PORT="6379" \
  KAFKA_BOOTSTRAP_SERVERS="127.0.0.1:29092" \
  APP_GATEWAY_SECURITY_ENABLED="false" \
  HOTDEAL_QUEUE_SSE_TOPIC="$topic" \
  HOTDEAL_QUEUE_SSE_HEARTBEAT_INTERVAL_MS="5000" \
  HOTDEAL_QUEUE_SSE_TIMEOUT_MS="180000" \
  SNOWFLAKE_WORKER_ID="$worker" \
  java -jar "$HOTDEAL_JAR" >"$log_file" 2>&1 &
  HOTDEAL_PIDS+=("$!")
}

wait_hotdeal_ready() {
  local port="$1"
  local health="http://127.0.0.1:${port}/actuator/health"
  wait_http_ok "$health" 240 || fail "hot-deal not ready on port ${port} (${health})"
}

stop_hotdeal_instances() {
  for pid in "${HOTDEAL_PIDS[@]:-}"; do
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
    fi
  done
  for pid in "${HOTDEAL_PIDS[@]:-}"; do
    wait "$pid" 2>/dev/null || true
  done
  HOTDEAL_PIDS=()
}

start_case_instances() {
  local mode="$1"
  local t1 t2 t3
  if [[ "$mode" == "before" ]]; then
    t1="hotdeal-queue-sse-events-a"
    t2="hotdeal-queue-sse-events-b"
    t3="hotdeal-queue-sse-events-c"
  else
    t1="hotdeal-queue-sse-events"
    t2="hotdeal-queue-sse-events"
    t3="hotdeal-queue-sse-events"
  fi
  CASE_TOPICS="${t1},${t2},${t3}"

  info "starting hot-deal instances mode=${mode}"
  start_hotdeal_instance "$PORT1" "$t1" "11" "$LOG_DIR/hotdeal-${mode}-${PORT1}.log"
  start_hotdeal_instance "$PORT2" "$t2" "12" "$LOG_DIR/hotdeal-${mode}-${PORT2}.log"
  start_hotdeal_instance "$PORT3" "$t3" "13" "$LOG_DIR/hotdeal-${mode}-${PORT3}.log"

  wait_hotdeal_ready "$PORT1"
  wait_hotdeal_ready "$PORT2"
  wait_hotdeal_ready "$PORT3"
  info "hot-deal instances ready mode=${mode}"
}

compile_probe() {
  info "compiling SseFanoutProbe.java"
  javac -d "$CLASS_DIR" docker/benchmark/SseFanoutProbe.java
}

build_hotdeal_jar() {
  info "building hot-deal bootJar (one-time)"
  ./gradlew :servers:services:hot-deal:bootJar --no-daemon >/dev/null
  HOTDEAL_JAR="$(find servers/services/hot-deal/build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*plain*' | head -n1)"
  [[ -n "$HOTDEAL_JAR" ]] || fail "hot-deal bootJar not found"
  info "using hot-deal jar: $HOTDEAL_JAR"
}

run_probe_case() {
  local mode="$1"
  local output="$OUT_DIR/${mode}.json"
  info "running probe mode=${mode} users=${USERS} trigger=${TRIGGER_MODE} topics=${CASE_TOPICS}"
  java -cp "$CLASS_DIR" SseFanoutProbe \
    --bases "$BASES" \
    --users "$USERS" \
    --hotdeal-id "$HOTDEAL_ID" \
    --immediate-ms "$IMMEDIATE_MS" \
    --timeout-ms "$TIMEOUT_MS" \
    --seed "$SEED" \
    --trigger "$TRIGGER_MODE" \
    --redis-host "127.0.0.1" \
    --redis-port "6379" \
    --topics "$CASE_TOPICS" \
    --output "$output"
}

write_summary() {
  local before_json="$OUT_DIR/before.json"
  local after_json="$OUT_DIR/after.json"
  local summary_json="$OUT_DIR/summary.json"
  local summary_md="$OUT_DIR/summary.md"

  jq -n \
    --slurpfile before "$before_json" \
    --slurpfile after "$after_json" \
    '{
      scenario: {
        users: $before[0].users,
        hotDealId: $before[0].hotDealId,
        immediateWindowMs: $before[0].immediateWindowMs,
        convergenceTimeoutMs: $before[0].convergenceTimeoutMs
      },
      before: $before[0],
      after: $after[0],
      diff: {
        immediateSuccessRatePercentDelta: (($after[0].immediateSuccessRatePercent - $before[0].immediateSuccessRatePercent) | tonumber),
        eventualSuccessRatePercentDelta: (($after[0].eventualSuccessRatePercent - $before[0].eventualSuccessRatePercent) | tonumber),
        convergenceP95MsDelta: (($after[0].convergenceLatencyMs.p95 - $before[0].convergenceLatencyMs.p95) | tonumber),
        notDeliveredCountDelta: (($after[0].notDeliveredCount - $before[0].notDeliveredCount) | tonumber)
      }
    }' >"$summary_json"

  {
    echo "# HotDeal SSE Fan-out Measurement Summary"
    echo
    echo "- users: $(jq -r '.scenario.users' "$summary_json")"
    echo "- hotDealId: $(jq -r '.scenario.hotDealId' "$summary_json")"
    echo "- immediateWindowMs: $(jq -r '.scenario.immediateWindowMs' "$summary_json")"
    echo "- convergenceTimeoutMs: $(jq -r '.scenario.convergenceTimeoutMs' "$summary_json")"
    echo
    echo "| metric | before (fan-out off simulation) | after (fan-out on) | delta |"
    echo "|---|---:|---:|---:|"
    echo "| immediate success rate (%) | $(jq -r '.before.immediateSuccessRatePercent' "$summary_json") | $(jq -r '.after.immediateSuccessRatePercent' "$summary_json") | $(jq -r '.diff.immediateSuccessRatePercentDelta' "$summary_json") |"
    echo "| eventual success rate (%) | $(jq -r '.before.eventualSuccessRatePercent' "$summary_json") | $(jq -r '.after.eventualSuccessRatePercent' "$summary_json") | $(jq -r '.diff.eventualSuccessRatePercentDelta' "$summary_json") |"
    echo "| convergence p95 (ms) | $(jq -r '.before.convergenceLatencyMs.p95' "$summary_json") | $(jq -r '.after.convergenceLatencyMs.p95' "$summary_json") | $(jq -r '.diff.convergenceP95MsDelta' "$summary_json") |"
    echo "| not delivered count | $(jq -r '.before.notDeliveredCount' "$summary_json") | $(jq -r '.after.notDeliveredCount' "$summary_json") | $(jq -r '.diff.notDeliveredCountDelta' "$summary_json") |"
    echo
    echo "output:"
    echo "- before: $before_json"
    echo "- after: $after_json"
    echo "- summary: $summary_json"
  } >"$summary_md"

  info "summary written: $summary_json"
  info "summary markdown: $summary_md"
}

main() {
  ensure_infra
  build_hotdeal_jar
  compile_probe

  clear_queue_keys "$HOTDEAL_ID"
  start_case_instances "before"
  run_probe_case "before"
  stop_hotdeal_instances

  clear_queue_keys "$HOTDEAL_ID"
  start_case_instances "after"
  run_probe_case "after"
  stop_hotdeal_instances

  write_summary

  info "done. out_dir=$OUT_DIR"
}

main "$@"
