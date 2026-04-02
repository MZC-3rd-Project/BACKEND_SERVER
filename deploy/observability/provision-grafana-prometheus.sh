#!/usr/bin/env bash
set -euo pipefail

AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-ap-northeast-2}"
AWS_PROFILE="${AWS_PROFILE:-${aws_profile:-}}"
GRAFANA_URL="${GRAFANA_URL:?GRAFANA_URL is required}"
GRAFANA_ADMIN_PARAM_NAME="${GRAFANA_ADMIN_PARAM_NAME:-/donmoa/dev/grafana/admin-password}"
SYSTEM_NAMESPACE="${SYSTEM_NAMESPACE:-donmoa-system}"
PROMETHEUS_SERVICE_NAME="${PROMETHEUS_SERVICE_NAME:-donmoa-monitoring-prometheus}"
PROMETHEUS_URL="${PROMETHEUS_URL:-}"
FOLDER_UID="${FOLDER_UID:-donmoa}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

resolve_load_balancer_endpoint() {
  local service_name="$1"
  local namespace="$2"
  local timeout_seconds="${3:-300}"
  local start_ts endpoint

  if ! command -v kubectl >/dev/null 2>&1; then
    echo "[ERROR] kubectl is required to resolve ${namespace}/${service_name}" >&2
    exit 1
  fi

  start_ts="$(date +%s)"

  while true; do
    endpoint="$(kubectl get service "${service_name}" \
      --namespace "${namespace}" \
      -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || true)"
    if [[ -n "${endpoint}" ]]; then
      echo "${endpoint}"
      return
    fi

    endpoint="$(kubectl get service "${service_name}" \
      --namespace "${namespace}" \
      -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || true)"
    if [[ -n "${endpoint}" ]]; then
      echo "${endpoint}"
      return
    fi

    if (( $(date +%s) - start_ts >= timeout_seconds )); then
      echo "[ERROR] Timed out waiting for ${namespace}/${service_name} load balancer endpoint" >&2
      exit 1
    fi

    sleep 5
  done
}

ensure_prometheus_url() {
  if [[ -n "${PROMETHEUS_URL}" ]]; then
    return
  fi

  PROMETHEUS_URL="$(resolve_load_balancer_endpoint "${PROMETHEUS_SERVICE_NAME}" "${SYSTEM_NAMESPACE}")"
}

ensure_prometheus_url

PASSWORD="$(env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
  aws ssm get-parameter \
  --name "${GRAFANA_ADMIN_PARAM_NAME}" \
  --with-decryption \
  --query 'Parameter.Value' \
  --output text)"

cat >/tmp/grafana-prometheus-datasource.json <<EOF
{
  "name": "Prometheus",
  "type": "prometheus",
  "access": "proxy",
  "url": "http://${PROMETHEUS_URL}:9090",
  "isDefault": false,
  "editable": false,
  "jsonData": {
    "httpMethod": "POST"
  }
}
EOF

EXISTING_PROM_UID="$(curl -sS -u "admin:${PASSWORD}" "${GRAFANA_URL}/api/datasources/name/Prometheus" | jq -r '.uid // empty')"

if [[ -n "${EXISTING_PROM_UID}" ]]; then
  curl -sS -u "admin:${PASSWORD}" \
    -H 'Content-Type: application/json' \
    -X PUT \
    "${GRAFANA_URL}/api/datasources/uid/${EXISTING_PROM_UID}" \
    -d @/tmp/grafana-prometheus-datasource.json >/tmp/grafana-prometheus-datasource-response.json
else
  curl -sS -u "admin:${PASSWORD}" \
    -H 'Content-Type: application/json' \
    -X POST \
    "${GRAFANA_URL}/api/datasources" \
    -d @/tmp/grafana-prometheus-datasource.json >/tmp/grafana-prometheus-datasource-response.json
fi

PROM_UID="$(curl -sS -u "admin:${PASSWORD}" "${GRAFANA_URL}/api/datasources/name/Prometheus" | jq -r '.uid // empty')"
if [[ -z "${PROM_UID}" ]]; then
  echo "[ERROR] Prometheus datasource not found after provisioning" >&2
  cat /tmp/grafana-prometheus-datasource-response.json >&2 || true
  exit 1
fi

DASHBOARD_JSON="$(sed "s/__PROM_UID__/${PROM_UID}/g" "${ROOT_DIR}/deploy/observability/grafana-donmoa-metrics-dashboard.json")"
PAYLOAD="$(jq -n \
  --argjson dashboard "${DASHBOARD_JSON}" \
  --arg folderUid "${FOLDER_UID}" \
  '{dashboard:$dashboard, folderUid:$folderUid, overwrite:true}')"

curl -sS -u "admin:${PASSWORD}" \
  -H 'Content-Type: application/json' \
  -X POST \
  "${GRAFANA_URL}/api/dashboards/db" \
  -d "${PAYLOAD}" >/tmp/grafana-prometheus-dashboard-response.json

DASHBOARD_UID="$(jq -r '.uid' /tmp/grafana-prometheus-dashboard-response.json)"
if [[ -z "${DASHBOARD_UID}" || "${DASHBOARD_UID}" == "null" ]]; then
  echo "[ERROR] Prometheus dashboard provisioning failed" >&2
  cat /tmp/grafana-prometheus-dashboard-response.json >&2
  exit 1
fi

echo "PROMETHEUS_UID=${PROM_UID}"
echo "DASHBOARD_UID=${DASHBOARD_UID}"
echo "PROMETHEUS_URL=${PROMETHEUS_URL}"
