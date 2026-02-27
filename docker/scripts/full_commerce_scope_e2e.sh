#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOG_DIR="${LOG_DIR:-/tmp/full_commerce_scope_e2e_logs_$(date +%Y%m%d_%H%M%S)}"
mkdir -p "$LOG_DIR"
COMPOSE_FILE="$ROOT/docker/docker-compose-local.yml"

INCLUDE_FULL_STACK="${INCLUDE_FULL_STACK:-true}"
STOP_ON_FAIL="${STOP_ON_FAIL:-false}"

PASSES=0
FAILURES=0

ALL_PORTS=(
  18084 18085 18086 18087 18088 18089
  18093 18095
  18173 18180 18181
  18284 18285 18286 18289
  18484
  18673 18684 18686 18688 18689
  18884 18888
)

pass() {
  PASSES=$((PASSES + 1))
  echo "[PASS] $1"
}

fail() {
  FAILURES=$((FAILURES + 1))
  echo "[FAIL] $1"
}

kill_listeners_on_known_ports() {
  local port pids
  for port in "${ALL_PORTS[@]}"; do
    pids="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
    if [[ -n "$pids" ]]; then
      echo "[INFO] killing listeners on port ${port}: ${pids}"
      # shellcheck disable=SC2086
      kill -9 $pids >/dev/null 2>&1 || true
    fi
  done
}

restart_postgres_if_running() {
  local pg_state=""
  if ! command -v docker >/dev/null 2>&1; then
    return 0
  fi

  if ! docker ps -a --format '{{.Names}}' 2>/dev/null | grep -qx "project03-postgres"; then
    return 0
  fi

  echo "[INFO] restarting postgres to reset leaked client connections"
  docker compose -f "$COMPOSE_FILE" restart postgres >/dev/null 2>&1 || true

  for _ in {1..30}; do
    pg_state="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' project03-postgres 2>/dev/null || true)"
    if [[ "$pg_state" == "healthy" || "$pg_state" == "running" ]]; then
      echo "[INFO] postgres state: ${pg_state}"
      return 0
    fi
    sleep 1
  done

  echo "[WARN] postgres health wait timed out (state=${pg_state:-unknown})"
  return 0
}

cleanup_between_scenarios() {
  echo "[INFO] scenario cleanup: listeners + postgres reset"
  kill_listeners_on_known_ports
  sleep 1
  restart_postgres_if_running
}

run_script() {
  local script_name="$1"
  local script_path="$ROOT/docker/scripts/${script_name}"
  local log_file="$LOG_DIR/${script_name%.sh}.log"
  local started_at ended_at duration

  if [[ ! -f "$script_path" ]]; then
    fail "missing script: ${script_name}"
    return 1
  fi

  started_at="$(date +%s)"
  echo "[INFO] running ${script_name}"
  if bash "$script_path" >"$log_file" 2>&1; then
    ended_at="$(date +%s)"
    duration=$((ended_at - started_at))
    pass "${script_name} (${duration}s)"
    return 0
  fi

  ended_at="$(date +%s)"
  duration=$((ended_at - started_at))
  fail "${script_name} (${duration}s)"
  echo "[INFO] last 80 lines from ${script_name}:"
  tail -n 80 "$log_file" || true
  return 1
}

main() {
  local -a scripts=(
    "gateway_session_trusted_auth_verify.sh"
    "gateway_session_userid_itemsearch_e2e.sh"
    "gateway_session_forced_trigger_catalog_e2e.sh"
    "funding_hotdeal_business_e2e.sh"
    "search_consumer_dlq_retry_e2e.sh"
    "outbox_broker_recovery_e2e.sh"
    "gateway_seller_dashboard_e2e_verify.sh"
  )

  if [[ "$INCLUDE_FULL_STACK" == "true" ]]; then
    scripts+=("full_stack_publish_chain_e2e.sh")
  fi

  cleanup_between_scenarios
  for script_name in "${scripts[@]}"; do
    if ! run_script "$script_name"; then
      if [[ "$STOP_ON_FAIL" == "true" ]]; then
        echo "[INFO] stop on fail enabled"
        break
      fi
    fi
    cleanup_between_scenarios
  done

  echo "[INFO] summary: passes=${PASSES}, failures=${FAILURES}"
  echo "[INFO] logs: ${LOG_DIR}"
  if (( FAILURES > 0 )); then
    exit 1
  fi
}

main "$@"
