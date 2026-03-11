#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOG_DIR="${LOG_DIR:-/tmp/db_lock_contention_compare_$(date +%Y%m%d_%H%M%S)}"
mkdir -p "$LOG_DIR"

CASE_SEQUENCE="${CASE_SEQUENCE:-single_db,split_read}"
WRITE_CONTAINER="${WRITE_CONTAINER:-project03-postgres}"
READ_CONTAINER="${READ_CONTAINER:-project03-postgres-read}"
DB_NAME="${DB_NAME:-product_db}"
TABLE_NAME="${TABLE_NAME:-lock_bench_counter}"

WRITE_WORKERS="${WRITE_WORKERS:-10}"
WRITE_ITERATIONS="${WRITE_ITERATIONS:-120}"
READ_WORKERS="${READ_WORKERS:-24}"
READ_WORKER_SLEEP_SECONDS="${READ_WORKER_SLEEP_SECONDS:-0.003}"
LOCK_HOLD_SECONDS="${LOCK_HOLD_SECONDS:-0.08}"
LOCKER_SLEEP_SECONDS="${LOCKER_SLEEP_SECONDS:-0.01}"
SAMPLE_INTERVAL_SECONDS="${SAMPLE_INTERVAL_SECONDS:-0.20}"

PIDS=()
CURRENT_WRITER_PIDS=()
CURRENT_READER_PIDS=()
CURRENT_LOCKER_PID=""
CURRENT_SAMPLER_PID=""
CASE_SUMMARY_FILES=()
PASSES=0
FAILURES=0

IFS=',' read -r -a CASES <<< "$CASE_SEQUENCE"

pass() {
  PASSES=$((PASSES + 1))
  echo "[PASS] $1"
}

fail() {
  FAILURES=$((FAILURES + 1))
  echo "[FAIL] $1" >&2
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

psql_exec_in() {
  local container="$1"
  local db="$2"
  local sql="$3"
  docker exec -i "$container" psql -U postgres -d "$db" -v ON_ERROR_STOP=1 -c "$sql" >/dev/null
}

psql_query_in() {
  local container="$1"
  local db="$2"
  local sql="$3"
  docker exec -i "$container" psql -U postgres -d "$db" -t -A -F',' -v ON_ERROR_STOP=1 -c "$sql" 2>/dev/null \
    | sed '/^\s*$/d' | head -n1
}

calc_p95_ms() {
  local file="$1"
  if [[ ! -s "$file" ]]; then
    echo "NA"
    return 0
  fi
  sort -n "$file" | awk '
    { a[NR] = $1 }
    END {
      if (NR == 0) {
        print "NA";
        exit;
      }
      idx = int((NR * 95 + 99) / 100);
      if (idx < 1) idx = 1;
      if (idx > NR) idx = NR;
      print a[idx];
    }
  '
}

calc_avg_ms() {
  local file="$1"
  if [[ ! -s "$file" ]]; then
    echo "0.000"
    return 0
  fi
  awk '{sum+=$1; count++} END { if (count==0) {print "0.000"} else {printf "%.3f\n", sum/count} }' "$file"
}

avg_max_from_csv_col() {
  local file="$1"
  local col="$2"
  if [[ ! -s "$file" ]]; then
    echo "0.00,0.00"
    return 0
  fi
  awk -F',' -v c="$col" '
    NR > 1 && $c ~ /^-?[0-9]+(\.[0-9]+)?$/ {
      sum += $c;
      count += 1;
      if (count == 1 || $c > max) max = $c;
    }
    END {
      if (count == 0) {
        print "0.00,0.00";
      } else {
        printf "%.2f,%.2f\n", (sum / count), max;
      }
    }
  ' "$file"
}

rate_percent() {
  local numerator="$1"
  local denominator="$2"
  awk -v n="$numerator" -v d="$denominator" 'BEGIN {
    if (d <= 0) { printf "0.00"; exit }
    printf "%.2f", (n * 100.0 / d)
  }'
}

get_xact_counts() {
  local container="$1"
  local row
  row="$(psql_query_in "$container" postgres \
    "SELECT COALESCE(xact_commit, 0), COALESCE(xact_rollback, 0) FROM pg_stat_database WHERE datname='${DB_NAME}';" || true)"
  if [[ -z "$row" ]]; then
    echo "0,0"
    return 0
  fi
  echo "$row"
}

prepare_case_data() {
  local case_name="$1"
  local sql

  sql="
    DROP TABLE IF EXISTS ${TABLE_NAME};
    CREATE TABLE ${TABLE_NAME} (
      id INTEGER PRIMARY KEY,
      counter BIGINT NOT NULL,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
    );
    INSERT INTO ${TABLE_NAME}(id, counter) VALUES (1, 0);
  "
  psql_exec_in "$WRITE_CONTAINER" "$DB_NAME" "$sql"

  if [[ "$case_name" == "split_read" ]]; then
    psql_exec_in "$READ_CONTAINER" "$DB_NAME" "$sql"
  fi

  psql_exec_in "$WRITE_CONTAINER" postgres "SELECT pg_stat_reset();"
  psql_exec_in "$READ_CONTAINER" postgres "SELECT pg_stat_reset();"
}

start_writer_sampler() {
  local case_dir="$1"
  local run_flag="${case_dir}/.writer_sampler_running"
  local sample_file="${case_dir}/writer_samples.csv"
  : > "$run_flag"
  echo "ts_epoch,active_conn,lock_wait_sessions,waiting_locks,cpu_percent" > "$sample_file"

  (
    local sql metrics active lock_wait waiting_locks cpu_raw cpu
    sql="SELECT
      (SELECT COUNT(*) FROM pg_stat_activity WHERE datname='${DB_NAME}' AND state='active') AS active_conn,
      (SELECT COUNT(*) FROM pg_stat_activity WHERE datname='${DB_NAME}' AND wait_event_type='Lock') AS lock_wait_sessions,
      (SELECT COUNT(*) FROM pg_locks l JOIN pg_database d ON l.database = d.oid WHERE d.datname='${DB_NAME}' AND NOT l.granted) AS waiting_locks;"

    while [[ -f "$run_flag" ]]; do
      metrics="$(psql_query_in "$WRITE_CONTAINER" postgres "$sql" || true)"
      active="$(echo "$metrics" | awk -F',' '{print $1}')"
      lock_wait="$(echo "$metrics" | awk -F',' '{print $2}')"
      waiting_locks="$(echo "$metrics" | awk -F',' '{print $3}')"
      [[ -z "$active" ]] && active=0
      [[ -z "$lock_wait" ]] && lock_wait=0
      [[ -z "$waiting_locks" ]] && waiting_locks=0

      cpu_raw="$(docker stats --no-stream --format '{{.CPUPerc}}' "$WRITE_CONTAINER" 2>/dev/null | head -n1 || true)"
      cpu="${cpu_raw%\%}"
      [[ -z "$cpu" ]] && cpu=0
      if ! [[ "$cpu" =~ ^-?[0-9]+(\.[0-9]+)?$ ]]; then
        cpu=0
      fi

      printf '%s,%s,%s,%s,%s\n' "$(date +%s)" "$active" "$lock_wait" "$waiting_locks" "$cpu" >> "$sample_file"
      sleep "$SAMPLE_INTERVAL_SECONDS"
    done
  ) &

  CURRENT_SAMPLER_PID="$!"
  PIDS+=("$CURRENT_SAMPLER_PID")
}

stop_writer_sampler() {
  local case_dir="$1"
  local run_flag="${case_dir}/.writer_sampler_running"
  rm -f "$run_flag"
  if [[ -n "$CURRENT_SAMPLER_PID" ]]; then
    wait "$CURRENT_SAMPLER_PID" 2>/dev/null || true
  fi
  CURRENT_SAMPLER_PID=""
}

start_locker() {
  local case_dir="$1"
  local run_flag="${case_dir}/.locker_running"
  local locker_log="${case_dir}/locker.log"
  : > "$run_flag"
  : > "$locker_log"

  (
    local sql
    sql="BEGIN; UPDATE ${TABLE_NAME} SET counter = counter + 1, updated_at = clock_timestamp() WHERE id=1; SELECT pg_sleep(${LOCK_HOLD_SECONDS}); COMMIT;"
    while [[ -f "$run_flag" ]]; do
      if docker exec -i "$WRITE_CONTAINER" psql -U postgres -d "$DB_NAME" -v ON_ERROR_STOP=1 -c "$sql" >/dev/null 2>&1; then
        echo "ok" >> "$locker_log"
      else
        echo "fail" >> "$locker_log"
      fi
      sleep "$LOCKER_SLEEP_SECONDS"
    done
  ) &

  CURRENT_LOCKER_PID="$!"
  PIDS+=("$CURRENT_LOCKER_PID")
}

stop_locker() {
  local case_dir="$1"
  local run_flag="${case_dir}/.locker_running"
  rm -f "$run_flag"
  if [[ -n "$CURRENT_LOCKER_PID" ]]; then
    wait "$CURRENT_LOCKER_PID" 2>/dev/null || true
  fi
  CURRENT_LOCKER_PID=""
}

start_writer_workers() {
  local case_dir="$1"
  CURRENT_WRITER_PIDS=()

  local worker_idx
  for worker_idx in $(seq 1 "$WRITE_WORKERS"); do
    local worker_file="${case_dir}/write_worker_${worker_idx}.log"
    : > "$worker_file"
    (
      local iter start_ns end_ns elapsed_ms
      for iter in $(seq 1 "$WRITE_ITERATIONS"); do
        start_ns="$(date +%s%N)"
        if docker exec -i "$WRITE_CONTAINER" psql -U postgres -d "$DB_NAME" -v ON_ERROR_STOP=1 \
          -c "UPDATE ${TABLE_NAME} SET counter = counter + 1, updated_at = clock_timestamp() WHERE id=1;" >/dev/null 2>&1; then
          :
        else
          echo "500 0.000" >> "$worker_file"
          continue
        fi
        end_ns="$(date +%s%N)"
        elapsed_ms="$(awk -v s="$start_ns" -v e="$end_ns" 'BEGIN { printf "%.3f", (e - s) / 1000000.0 }')"
        echo "200 ${elapsed_ms}" >> "$worker_file"
      done
    ) &
    CURRENT_WRITER_PIDS+=("$!")
    PIDS+=("$!")
  done
}

wait_writer_workers() {
  local pid
  for pid in "${CURRENT_WRITER_PIDS[@]:-}"; do
    wait "$pid" 2>/dev/null || true
  done
  CURRENT_WRITER_PIDS=()
}

start_read_workers() {
  local case_name="$1"
  local case_dir="$2"
  local target_container
  local run_flag="${case_dir}/.read_workers_running"
  : > "$run_flag"

  case "$case_name" in
    single_db)
      target_container="$WRITE_CONTAINER"
      ;;
    split_read)
      target_container="$READ_CONTAINER"
      ;;
    *)
      target_container="$WRITE_CONTAINER"
      ;;
  esac

  CURRENT_READER_PIDS=()
  local worker_idx
  for worker_idx in $(seq 1 "$READ_WORKERS"); do
    local worker_file="${case_dir}/read_worker_${worker_idx}.log"
    : > "$worker_file"
    (
      local start_ns end_ns elapsed_ms
      while [[ -f "$run_flag" ]]; do
        start_ns="$(date +%s%N)"
        if docker exec -i "$target_container" psql -U postgres -d "$DB_NAME" -v ON_ERROR_STOP=1 \
          -t -A -c "SELECT counter FROM ${TABLE_NAME} WHERE id=1;" >/dev/null 2>&1; then
          end_ns="$(date +%s%N)"
          elapsed_ms="$(awk -v s="$start_ns" -v e="$end_ns" 'BEGIN { printf "%.3f", (e - s) / 1000000.0 }')"
          echo "200 ${elapsed_ms}" >> "$worker_file"
        else
          echo "500 0.000" >> "$worker_file"
        fi
        sleep "$READ_WORKER_SLEEP_SECONDS"
      done
    ) &
    CURRENT_READER_PIDS+=("$!")
    PIDS+=("$!")
  done
}

stop_read_workers() {
  local case_dir="$1"
  local run_flag="${case_dir}/.read_workers_running"
  rm -f "$run_flag"
  local pid
  for pid in "${CURRENT_READER_PIDS[@]:-}"; do
    wait "$pid" 2>/dev/null || true
  done
  CURRENT_READER_PIDS=()
}

summarize_ops_log() {
  local input_file="$1"
  local ms_file="$2"
  : > "$ms_file"

  local total success fail avg_ms p95_ms
  total="$(wc -l < "$input_file" 2>/dev/null | tr -d ' ')"
  success="$(awk '$1=="200"{c++} END{print c+0}' "$input_file" 2>/dev/null)"
  fail=$((total - success))
  awk '$1=="200" && $2 ~ /^[0-9.]+$/ { print $2 }' "$input_file" >> "$ms_file" 2>/dev/null || true
  avg_ms="$(calc_avg_ms "$ms_file")"
  p95_ms="$(calc_p95_ms "$ms_file")"
  printf '%s %s %s %s %s\n' "$total" "$success" "$fail" "$avg_ms" "$p95_ms"
}

run_case() {
  local case_name="$1"
  local case_dir="${LOG_DIR}/case_${case_name}"
  mkdir -p "$case_dir"

  echo "[INFO] ===== lock contention case=${case_name} ====="
  prepare_case_data "$case_name"
  pass "prepared case data for ${case_name}"

  local write_xact_start read_xact_start
  write_xact_start="$(get_xact_counts "$WRITE_CONTAINER")"
  read_xact_start="$(get_xact_counts "$READ_CONTAINER")"

  start_writer_sampler "$case_dir"
  start_locker "$case_dir"
  start_read_workers "$case_name" "$case_dir"
  start_writer_workers "$case_dir"
  wait_writer_workers
  stop_read_workers "$case_dir"
  stop_locker "$case_dir"
  stop_writer_sampler "$case_dir"

  local write_xact_end read_xact_end
  write_xact_end="$(get_xact_counts "$WRITE_CONTAINER")"
  read_xact_end="$(get_xact_counts "$READ_CONTAINER")"

  local write_commit_start write_rollback_start write_commit_end write_rollback_end
  local read_commit_start read_rollback_start read_commit_end read_rollback_end
  IFS=',' read -r write_commit_start write_rollback_start <<< "$write_xact_start"
  IFS=',' read -r write_commit_end write_rollback_end <<< "$write_xact_end"
  IFS=',' read -r read_commit_start read_rollback_start <<< "$read_xact_start"
  IFS=',' read -r read_commit_end read_rollback_end <<< "$read_xact_end"

  local writer_xact_delta read_xact_delta
  writer_xact_delta=$(( (write_commit_end + write_rollback_end) - (write_commit_start + write_rollback_start) ))
  read_xact_delta=$(( (read_commit_end + read_rollback_end) - (read_commit_start + read_rollback_start) ))

  local merged_write="${case_dir}/write_ops_merged.log"
  local merged_read="${case_dir}/read_ops_merged.log"
  cat "${case_dir}"/write_worker_*.log 2>/dev/null > "$merged_write" || true
  cat "${case_dir}"/read_worker_*.log 2>/dev/null > "$merged_read" || true

  local write_ms_file="${case_dir}/write_ops_ms.txt"
  local read_ms_file="${case_dir}/read_ops_ms.txt"
  local write_total write_success write_fail write_avg write_p95
  local read_total read_success read_fail read_avg read_p95
  read -r write_total write_success write_fail write_avg write_p95 < <(summarize_ops_log "$merged_write" "$write_ms_file")
  read -r read_total read_success read_fail read_avg read_p95 < <(summarize_ops_log "$merged_read" "$read_ms_file")

  local write_error_rate read_error_rate
  write_error_rate="$(rate_percent "$write_fail" "$write_total")"
  read_error_rate="$(rate_percent "$read_fail" "$read_total")"

  local writer_samples="${case_dir}/writer_samples.csv"
  local active_avg active_max lock_wait_avg lock_wait_max waiting_locks_avg waiting_locks_max cpu_avg cpu_max
  IFS=',' read -r active_avg active_max < <(avg_max_from_csv_col "$writer_samples" 2)
  IFS=',' read -r lock_wait_avg lock_wait_max < <(avg_max_from_csv_col "$writer_samples" 3)
  IFS=',' read -r waiting_locks_avg waiting_locks_max < <(avg_max_from_csv_col "$writer_samples" 4)
  IFS=',' read -r cpu_avg cpu_max < <(avg_max_from_csv_col "$writer_samples" 5)

  local counter_value
  counter_value="$(psql_query_in "$WRITE_CONTAINER" "$DB_NAME" "SELECT counter FROM ${TABLE_NAME} WHERE id=1;" || true)"
  [[ -z "$counter_value" ]] && counter_value=0

  local summary_file="${case_dir}/summary.json"
  cat > "$summary_file" <<JSON
{
  "case": "${case_name}",
  "write_workers": ${WRITE_WORKERS},
  "write_iterations_per_worker": ${WRITE_ITERATIONS},
  "read_workers": ${READ_WORKERS},
  "lock_hold_seconds": ${LOCK_HOLD_SECONDS},
  "write_total_requests": ${write_total},
  "write_success_count": ${write_success},
  "write_fail_count": ${write_fail},
  "write_error_rate_percent": ${write_error_rate},
  "write_avg_ms": "${write_avg}",
  "write_p95_ms": "${write_p95}",
  "read_total_requests": ${read_total},
  "read_success_count": ${read_success},
  "read_fail_count": ${read_fail},
  "read_error_rate_percent": ${read_error_rate},
  "read_avg_ms": "${read_avg}",
  "read_p95_ms": "${read_p95}",
  "writer_active_conn_avg": ${active_avg},
  "writer_active_conn_max": ${active_max},
  "writer_lock_wait_sessions_avg": ${lock_wait_avg},
  "writer_lock_wait_sessions_max": ${lock_wait_max},
  "writer_waiting_locks_avg": ${waiting_locks_avg},
  "writer_waiting_locks_max": ${waiting_locks_max},
  "writer_cpu_avg_percent": ${cpu_avg},
  "writer_cpu_max_percent": ${cpu_max},
  "writer_xact_delta": ${writer_xact_delta},
  "read_xact_delta": ${read_xact_delta},
  "final_counter": ${counter_value}
}
JSON

  CASE_SUMMARY_FILES+=("$summary_file")
  pass "case ${case_name} complete"
}

generate_global_reports() {
  local global_json="${LOG_DIR}/db_lock_contention_summary.json"
  local global_md="${LOG_DIR}/db_lock_contention_summary.md"
  local chart_csv="${LOG_DIR}/db_lock_contention_chart.csv"

  jq -s --arg generatedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    '{ generated_at: $generatedAt, cases: . }' \
    "${CASE_SUMMARY_FILES[@]}" > "$global_json"

  {
    echo "# DB Lock Contention Comparison Summary"
    echo
    echo "- generated_at: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "- cases: ${CASE_SEQUENCE}"
    echo "- write_workers: ${WRITE_WORKERS}"
    echo "- write_iterations_per_worker: ${WRITE_ITERATIONS}"
    echo "- read_workers: ${READ_WORKERS}"
    echo "- lock_hold_seconds: ${LOCK_HOLD_SECONDS}"
    echo
    echo "| Case | Write p95 (ms) | Write avg (ms) | Read p95 (ms) | LockWait avg/max | WaitingLocks avg/max | Writer CPU avg/max (%) | Writer xact delta | Read xact delta | Write error % | Read error % |"
    echo "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|"
    jq -r '.cases[] |
      [
        .case,
        .write_p95_ms,
        .write_avg_ms,
        .read_p95_ms,
        ((.writer_lock_wait_sessions_avg|tostring) + " / " + (.writer_lock_wait_sessions_max|tostring)),
        ((.writer_waiting_locks_avg|tostring) + " / " + (.writer_waiting_locks_max|tostring)),
        ((.writer_cpu_avg_percent|tostring) + " / " + (.writer_cpu_max_percent|tostring)),
        .writer_xact_delta,
        .read_xact_delta,
        .write_error_rate_percent,
        .read_error_rate_percent
      ] | @tsv' "$global_json" \
      | while IFS=$'\t' read -r c1 c2 c3 c4 c5 c6 c7 c8 c9 c10 c11; do
          echo "| ${c1} | ${c2} | ${c3} | ${c4} | ${c5} | ${c6} | ${c7} | ${c8} | ${c9} | ${c10} | ${c11} |"
        done
    echo
    echo "## Notes"
    echo
    echo "- \`single_db\`: read/write가 동일 writer 노드에 집중."
    echo "- \`split_read\`: read를 read 노드로 분리해 writer의 경쟁 자원(CPU/connection) 부담 완화."
    echo "- lock wait 수치는 writer의 동일 row 업데이트 경쟁(의도적 lock holder + 동시 writers)에서 수집."
  } > "$global_md"

  cat > "$chart_csv" <<CSV
metric,single_db,split_read
write_p95_ms,$(jq -r '.cases[] | select(.case=="single_db") | .write_p95_ms' "$global_json"),$(jq -r '.cases[] | select(.case=="split_read") | .write_p95_ms' "$global_json")
lock_wait_sessions_max,$(jq -r '.cases[] | select(.case=="single_db") | .writer_lock_wait_sessions_max' "$global_json"),$(jq -r '.cases[] | select(.case=="split_read") | .writer_lock_wait_sessions_max' "$global_json")
waiting_locks_max,$(jq -r '.cases[] | select(.case=="single_db") | .writer_waiting_locks_max' "$global_json"),$(jq -r '.cases[] | select(.case=="split_read") | .writer_waiting_locks_max' "$global_json")
writer_cpu_avg_percent,$(jq -r '.cases[] | select(.case=="single_db") | .writer_cpu_avg_percent' "$global_json"),$(jq -r '.cases[] | select(.case=="split_read") | .writer_cpu_avg_percent' "$global_json")
CSV

  echo "[INFO] --- DB Lock Contention Summary ---"
  jq -r '.cases[] | "[INFO] case=\(.case) write_p95=\(.write_p95_ms)ms lock_wait_avg=\(.writer_lock_wait_sessions_avg) lock_wait_max=\(.writer_lock_wait_sessions_max) waiting_locks_max=\(.writer_waiting_locks_max) writer_cpu_avg=\(.writer_cpu_avg_percent)%"' "$global_json"
  echo "[INFO] json summary: ${global_json}"
  echo "[INFO] markdown summary: ${global_md}"
  echo "[INFO] chart csv: ${chart_csv}"
}

main() {
  require_cmd docker
  require_cmd jq
  require_cmd awk
  require_cmd sed
  require_cmd sort
  require_cmd wc

  echo "[INFO] bringing up infra (postgres, postgres-read, redis)"
  (cd "$ROOT/docker" && docker compose -f docker-compose.yml -f docker-compose.read-replica.yml up -d postgres postgres-read redis >/dev/null)

  local case_name
  for case_name in "${CASES[@]}"; do
    run_case "$case_name"
  done

  generate_global_reports
  pass "db lock contention benchmark completed"
  echo "[INFO] passes=${PASSES}, failures=${FAILURES}"

  if (( FAILURES > 0 )); then
    exit 1
  fi
}

main "$@"
