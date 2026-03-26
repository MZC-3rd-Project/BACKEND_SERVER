#!/usr/bin/env bash
set -euo pipefail

AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-ap-northeast-2}"
AWS_PROFILE="${AWS_PROFILE:-${aws_profile:-}}"
GRAFANA_URL="${GRAFANA_URL:?GRAFANA_URL is required}"
GRAFANA_ADMIN_PARAM_NAME="${GRAFANA_ADMIN_PARAM_NAME:-/donmoa/dev/grafana/admin-password}"
PROMETHEUS_URL="${PROMETHEUS_URL:?PROMETHEUS_URL is required}"
FOLDER_UID="${FOLDER_UID:-donmoa}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

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
  "url": "http://${PROMETHEUS_URL}",
  "isDefault": false,
  "editable": false,
  "jsonData": {
    "httpMethod": "POST"
  }
}
EOF

curl -sS -u "admin:${PASSWORD}" \
  -H 'Content-Type: application/json' \
  -X POST \
  "${GRAFANA_URL}/api/datasources" \
  -d @/tmp/grafana-prometheus-datasource.json >/tmp/grafana-prometheus-datasource-response.json || true

PROM_UID="$(curl -sS -u "admin:${PASSWORD}" "${GRAFANA_URL}/api/datasources/name/Prometheus" | jq -r '.uid')"
if [[ -z "${PROM_UID}" || "${PROM_UID}" == "null" ]]; then
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
