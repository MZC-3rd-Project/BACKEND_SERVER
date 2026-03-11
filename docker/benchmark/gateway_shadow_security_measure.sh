#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOG_DIR="${LOG_DIR:-/tmp/gateway_shadow_security_measure_$(date +%Y%m%d_%H%M%S)}"
mkdir -p "$LOG_DIR"

GW_PORT="${GW_PORT:-18173}"
CHAT_PORT="${CHAT_PORT:-18093}"
REDIS_CONTAINER="${REDIS_CONTAINER:-project03-redis}"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/.gradle-codex-security-measure}"
JWT_ROUNDS="${JWT_ROUNDS:-60}"
TRUSTED_ROUNDS="${TRUSTED_ROUNDS:-60}"
TEST_USER_ID="${TEST_USER_ID:-777}"
TEST_ROLES="${TEST_ROLES:-USER,BUYER}"
TEST_SID="${TEST_SID:-sid-shadow-measure-777}"

CSV_PATH="$LOG_DIR/security_shadow_runs.csv"
SUMMARY_JSON="$LOG_DIR/security_shadow_summary.json"
SUMMARY_MD="$LOG_DIR/security_shadow_summary.md"

PIDS=()
GW_PID=""
CHAT_PID=""
REQUEST_SEQ=0

log() {
  printf '[INFO] %s\n' "$1"
}

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
    echo "[ERR] port ${port} is already in use" >&2
    exit 1
  fi
}

wait_http_code_prefix() {
  local name="$1"
  local url="$2"
  local prefix="$3"
  local retries="${4:-150}"
  local code=""
  for _ in $(seq 1 "$retries"); do
    code="$(curl -sS -o /dev/null -w "%{http_code}" --max-time 2 "$url" || true)"
    if [[ "$code" == "$prefix"* ]]; then
      log "${name} ready (${code})"
      return 0
    fi
    sleep 1
  done
  echo "[ERR] ${name} readiness timeout (last=${code})" >&2
  return 1
}

b64url() {
  printf '%s' "$1" | openssl base64 -A | tr '+/' '-_' | tr -d '='
}

build_gateway_context() {
  local user_id="$1"
  local roles="$2"
  local nonce="$3"
  local timestamp="$4"
  local payload signature

  payload="${user_id}|${roles}|${nonce}|${timestamp}"
  signature="$(printf '%s' "$payload" | openssl dgst -binary -sha256 -hmac "$SIGNING_KEY" | openssl base64 -A)"

  printf '%s.%s.%s.%s.%s' \
    "$(b64url "$user_id")" \
    "$(b64url "$roles")" \
    "$(b64url "$nonce")" \
    "$timestamp" \
    "$signature"
}

secs_to_ms() {
  python3 - "$1" <<'PY'
import sys
try:
    sec = float(sys.argv[1])
except Exception:
    sec = 0.0
print(f"{sec * 1000.0:.3f}")
PY
}

stop_gateway() {
  if [[ -n "${GW_PID:-}" ]] && kill -0 "$GW_PID" 2>/dev/null; then
    kill "$GW_PID" 2>/dev/null || true
    wait "$GW_PID" 2>/dev/null || true
  fi
  sleep 1
  if [[ -n "${GW_PID:-}" ]] && kill -0 "$GW_PID" 2>/dev/null; then
    kill -9 "$GW_PID" 2>/dev/null || true
    wait "$GW_PID" 2>/dev/null || true
  fi
  GW_PID=""
}

cleanup() {
  stop_gateway
  if [[ -n "${CHAT_PID:-}" ]] && kill -0 "$CHAT_PID" 2>/dev/null; then
    kill "$CHAT_PID" 2>/dev/null || true
    wait "$CHAT_PID" 2>/dev/null || true
  fi
  sleep 1
  if [[ -n "${CHAT_PID:-}" ]] && kill -0 "$CHAT_PID" 2>/dev/null; then
    kill -9 "$CHAT_PID" 2>/dev/null || true
    wait "$CHAT_PID" 2>/dev/null || true
  fi
  printf '[INFO] logs: %s\n' "$LOG_DIR"
}
trap cleanup EXIT

start_chat() {
  log "starting chat-service on ${CHAT_PORT}"
  (
    cd "$ROOT"
    env \
      GRADLE_USER_HOME="$GRADLE_USER_HOME" \
      SERVER_PORT="$CHAT_PORT" \
      APP_SECURITY_CONTEXT_SIGNING_KEY="$SIGNING_KEY" \
      APP_SECURITY_CONTEXT_MAX_AGE_MILLIS="$MAX_AGE_MILLIS" \
      CHAT_SECURITY_GATEWAY_AUTH_ENABLED=true \
      ./gradlew :servers:services:chat:bootRun --no-daemon >"$LOG_DIR/chat.log" 2>&1
  ) &
  CHAT_PID="$!"
  PIDS+=("$CHAT_PID")
  wait_http_code_prefix "chat-service health" "http://127.0.0.1:${CHAT_PORT}/actuator/health" "200" 180
}

start_gateway_jwt_mode() {
  log "starting client-gateway in JWT mode on ${GW_PORT}"
  (
    cd "$ROOT"
    env \
      GRADLE_USER_HOME="$GRADLE_USER_HOME" \
      SERVER_PORT="$GW_PORT" \
      APP_SECURITY_CONTEXT_SIGNING_KEY="$SIGNING_KEY" \
      APP_SECURITY_CONTEXT_MAX_AGE_MILLIS="$MAX_AGE_MILLIS" \
      APP_SERVICE_CHAT_URL="http://127.0.0.1:${CHAT_PORT}" \
      APP_SERVICE_CHAT_WS_URL="ws://127.0.0.1:${CHAT_PORT}" \
      ./gradlew :servers:gateways:client-gateway:bootRun --no-daemon >"$LOG_DIR/gateway-jwt.log" 2>&1
  ) &
  GW_PID="$!"
  PIDS+=("$GW_PID")
  wait_http_code_prefix "client-gateway (jwt mode)" "http://127.0.0.1:${GW_PORT}/api/v1/chat/rooms?size=1" "401" 180
}

start_gateway_trusted_mode() {
  log "starting client-gateway in trusted-header mode on ${GW_PORT}"
  (
    cd "$ROOT"
    env \
      GRADLE_USER_HOME="$GRADLE_USER_HOME" \
      SERVER_PORT="$GW_PORT" \
      APP_SECURITY_CONTEXT_SIGNING_KEY="$SIGNING_KEY" \
      APP_SECURITY_CONTEXT_MAX_AGE_MILLIS="$MAX_AGE_MILLIS" \
      GATEWAY_SESSION_ENABLED=true \
      GATEWAY_SESSION_TRUSTED_HEADER_AUTH_ENABLED=true \
      APP_SERVICE_CHAT_URL="http://127.0.0.1:${CHAT_PORT}" \
      APP_SERVICE_CHAT_WS_URL="ws://127.0.0.1:${CHAT_PORT}" \
      ./gradlew :servers:gateways:client-gateway:bootRun --no-daemon >"$LOG_DIR/gateway-trusted.log" 2>&1
  ) &
  GW_PID="$!"
  PIDS+=("$GW_PID")
  wait_http_code_prefix "client-gateway (trusted mode)" "http://127.0.0.1:${GW_PORT}/api/v1/chat/rooms?size=1" "401" 180
}

request_case() {
  local phase="$1"
  local case_name="$2"
  local category="$3"
  local reason="$4"
  local expected_status="$5"
  shift 5

  REQUEST_SEQ=$((REQUEST_SEQ + 1))
  local body_file="$LOG_DIR/body_${REQUEST_SEQ}_${phase}_${case_name}.json"
  local out status time_s success time_ms

  out="$(curl -sS --max-time 10 -o "$body_file" -w '%{http_code} %{time_total}' "$@" || true)"
  if [[ -z "$out" ]]; then
    status="000"
    time_s="10"
  else
    status="${out%% *}"
    time_s="${out##* }"
  fi

  if [[ "$status" == "$expected_status" ]]; then
    success=1
  else
    success=0
  fi

  time_ms="$(secs_to_ms "$time_s")"
  printf '%s,%s,%s,%s,%s,%s,%s,%s\n' \
    "$phase" "$case_name" "$category" "$reason" "$status" "$expected_status" "$success" "$time_ms" >>"$CSV_PATH"
}

run_jwt_phase() {
  local i
  for i in $(seq 1 "$JWT_ROUNDS"); do
    request_case "jwt" "normal_valid_jwt" "normal" "none" "200" \
      -H "Authorization: Bearer ${TOKEN_OK}" \
      "http://127.0.0.1:${GW_PORT}/api/v1/chat/rooms?size=1"

    request_case "jwt" "attack_bad_claim_spoof" "attack" "jwt_invalid_claim" "401" \
      -H "Authorization: Bearer ${TOKEN_BAD}" \
      -H "X-User-Id: 999" \
      -H "X-User-Roles: ADMIN" \
      "http://127.0.0.1:${GW_PORT}/api/v1/chat/rooms?size=1"

    request_case "jwt" "attack_spoof_invalid_header" "attack" "header_spoof_sanitized" "200" \
      -H "Authorization: Bearer ${TOKEN_OK}" \
      -H "X-User-Id: not-a-number" \
      -H "X-User-Roles: ADMIN" \
      "http://127.0.0.1:${GW_PORT}/api/v1/chat/rooms?size=1"

    request_case "jwt" "attack_spoof_xss_header" "attack" "header_spoof_sanitized" "200" \
      -H "Authorization: Bearer ${TOKEN_OK}" \
      -H 'X-User-Id: <script>alert(1)</script>' \
      -H 'X-User-Roles: <img src=x onerror=alert(1)>' \
      "http://127.0.0.1:${GW_PORT}/api/v1/chat/rooms?size=1"
  done
}

run_trusted_phase() {
  local now_millis nonce trusted_context broken_context i
  now_millis="$(( $(date +%s) * 1000 ))"
  nonce="$(openssl rand -hex 8)"
  trusted_context="$(build_gateway_context "$TEST_USER_ID" "$TEST_ROLES" "$nonce" "$now_millis")"
  broken_context="${trusted_context}x"

  for i in $(seq 1 "$TRUSTED_ROUNDS"); do
    request_case "trusted" "normal_trusted_context_active_sid" "normal" "none" "200" \
      -H "X-Gateway-Context: ${trusted_context}" \
      -H "X-Session-Id: ${TEST_SID}" \
      "http://127.0.0.1:${GW_PORT}/api/v1/chat/rooms?size=1"

    request_case "trusted" "attack_unknown_session" "attack" "session_not_active" "401" \
      -H "X-Gateway-Context: ${trusted_context}" \
      -H "X-Session-Id: sid-not-exists" \
      "http://127.0.0.1:${GW_PORT}/api/v1/chat/rooms?size=1"

    request_case "trusted" "attack_broken_signature" "attack" "hmac_mismatch" "401" \
      -H "X-Gateway-Context: ${broken_context}" \
      -H "X-Session-Id: ${TEST_SID}" \
      "http://127.0.0.1:${GW_PORT}/api/v1/chat/rooms?size=1"

    request_case "trusted" "attack_missing_session_header" "attack" "missing_session_pair" "401" \
      -H "X-Gateway-Context: ${trusted_context}" \
      "http://127.0.0.1:${GW_PORT}/api/v1/chat/rooms?size=1"
  done
}

build_summary() {
  python3 - "$CSV_PATH" "$SUMMARY_JSON" "$SUMMARY_MD" "$JWT_ROUNDS" "$TRUSTED_ROUNDS" <<'PY'
import csv
import json
import math
import statistics
import sys
from collections import defaultdict
from pathlib import Path

csv_path = Path(sys.argv[1])
summary_json = Path(sys.argv[2])
summary_md = Path(sys.argv[3])
jwt_rounds = int(sys.argv[4])
trusted_rounds = int(sys.argv[5])

rows = []
with csv_path.open() as f:
    reader = csv.DictReader(f)
    for r in reader:
        r["success"] = int(r["success"])
        r["time_ms"] = float(r["time_ms"])
        rows.append(r)

if not rows:
    raise SystemExit("no rows captured")

def p95(values):
    if not values:
        return None
    values = sorted(values)
    idx = max(0, min(len(values) - 1, math.ceil(len(values) * 0.95) - 1))
    return values[idx]

attack = [r for r in rows if r["category"] == "attack"]
normal = [r for r in rows if r["category"] == "normal"]

attack_success = [r for r in attack if r["success"] == 1]
normal_fail = [r for r in normal if r["success"] == 0]

attack_total = len(attack)
normal_total = len(normal)
attack_detected = len(attack_success)
false_positive = len(normal_fail)

detection_rate = (attack_detected / attack_total * 100.0) if attack_total else 0.0
false_positive_rate = (false_positive / normal_total * 100.0) if normal_total else 0.0

attack_latency_p95 = p95([r["time_ms"] for r in attack_success])
normal_latency_p95 = p95([r["time_ms"] for r in normal])

case_total = sorted({r["case_name"] for r in rows})
case_passed = sorted({r["case_name"] for r in rows if r["success"] == 1})
scenario_coverage = (len(case_passed) / len(case_total) * 100.0) if case_total else 0.0

reason_stats = defaultdict(lambda: {"total": 0, "success": 0})
for r in attack:
    reason_stats[r["reason"]]["total"] += 1
    reason_stats[r["reason"]]["success"] += r["success"]

phase_stats = defaultdict(lambda: {"total": 0, "success": 0})
for r in rows:
    phase_stats[r["phase"]]["total"] += 1
    phase_stats[r["phase"]]["success"] += r["success"]

summary = {
    "scenario": {
        "jwt_rounds": jwt_rounds,
        "trusted_rounds": trusted_rounds,
        "total_requests": len(rows),
        "attack_requests": attack_total,
        "normal_requests": normal_total,
    },
    "metrics": {
        "attack_detection_rate_percent": round(detection_rate, 2),
        "normal_false_positive_rate_percent": round(false_positive_rate, 2),
        "attack_detection_response_p95_ms": round(attack_latency_p95 or 0.0, 3),
        "normal_response_p95_ms": round(normal_latency_p95 or 0.0, 3),
        "scenario_coverage_percent": round(scenario_coverage, 2),
        "scenario_coverage": {
            "passed": len(case_passed),
            "total": len(case_total),
            "cases": case_passed,
        },
    },
    "counts": {
        "attack_detected": attack_detected,
        "attack_total": attack_total,
        "false_positive": false_positive,
        "normal_total": normal_total,
    },
    "phase_stats": phase_stats,
    "reason_stats": {
        k: {
            "success": v["success"],
            "total": v["total"],
            "success_rate_percent": round((v["success"] / v["total"] * 100.0) if v["total"] else 0.0, 2),
        }
        for k, v in sorted(reason_stats.items())
    },
}

summary_json.write_text(json.dumps(summary, ensure_ascii=False, indent=2))

md = []
md.append("# Gateway Shadow Security Measure Summary")
md.append("")
md.append(f"- Total requests: {len(rows)}")
md.append(f"- Attack requests: {attack_total}")
md.append(f"- Normal requests: {normal_total}")
md.append("")
md.append("## Core metrics")
md.append("")
md.append(f"- Attack detection rate: {summary['metrics']['attack_detection_rate_percent']:.2f}% ({attack_detected}/{attack_total})")
md.append(f"- Normal false-positive rate: {summary['metrics']['normal_false_positive_rate_percent']:.2f}% ({false_positive}/{normal_total})")
md.append(f"- Detection response p95: {summary['metrics']['attack_detection_response_p95_ms']:.3f} ms")
md.append(f"- Scenario coverage: {summary['metrics']['scenario_coverage_percent']:.2f}% ({len(case_passed)}/{len(case_total)})")
md.append("")
md.append("## Reason breakdown")
md.append("")
md.append("| reason | success/total | success_rate |")
md.append("|---|---:|---:|")
for reason, stat in summary["reason_stats"].items():
    md.append(f"| {reason} | {stat['success']}/{stat['total']} | {stat['success_rate_percent']:.2f}% |")
md.append("")
summary_md.write_text("\n".join(md) + "\n")
PY
}

main() {
  require_cmd docker
  require_cmd jq
  require_cmd openssl
  require_cmd curl
  require_cmd lsof
  require_cmd python3

  if [[ ! -f "$ROOT/docker/.env" ]]; then
    echo "[ERR] docker/.env not found" >&2
    exit 1
  fi

  SIGNING_KEY="$(sed -n 's/^APP_SECURITY_CONTEXT_SIGNING_KEY=//p' "$ROOT/docker/.env" | head -n1)"
  MAX_AGE_MILLIS="$(sed -n 's/^APP_SECURITY_CONTEXT_MAX_AGE_MILLIS=//p' "$ROOT/docker/.env" | head -n1)"
  if [[ -z "$SIGNING_KEY" ]]; then
    echo "[ERR] APP_SECURITY_CONTEXT_SIGNING_KEY is empty in docker/.env" >&2
    exit 1
  fi
  if [[ -z "$MAX_AGE_MILLIS" ]]; then
    MAX_AGE_MILLIS="300000"
  fi

  check_port_free "$GW_PORT"
  check_port_free "$CHAT_PORT"

  log "preparing docker infra"
  (cd "$ROOT/docker" && docker compose up -d postgres redis zookeeper kafka mariadb >/dev/null)

  log "seeding redis session: $TEST_SID"
  docker exec -i "$REDIS_CONTAINER" redis-cli HSET "gateway:sess:${TEST_SID}" status ACTIVE >/dev/null

  printf 'phase,case_name,category,reason,status,expected_status,success,time_ms\n' >"$CSV_PATH"

  JWT_HEADER='{"alg":"none","typ":"JWT"}'
  JWT_PAYLOAD_OK='{"userId":777,"roles":["buyer","user"]}'
  JWT_PAYLOAD_BAD='{"sub":"not-number","roles":["buyer"]}'
  TOKEN_OK="$(b64url "$JWT_HEADER").$(b64url "$JWT_PAYLOAD_OK")."
  TOKEN_BAD="$(b64url "$JWT_HEADER").$(b64url "$JWT_PAYLOAD_BAD")."

  start_chat
  start_gateway_jwt_mode
  log "running JWT phase: rounds=${JWT_ROUNDS}"
  run_jwt_phase

  stop_gateway
  start_gateway_trusted_mode
  log "running trusted-header phase: rounds=${TRUSTED_ROUNDS}"
  run_trusted_phase

  build_summary

  log "summary json: $SUMMARY_JSON"
  log "summary md: $SUMMARY_MD"
  log "runs csv: $CSV_PATH"
}

main "$@"
