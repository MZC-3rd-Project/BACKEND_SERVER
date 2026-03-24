#!/usr/bin/env bash
set -euo pipefail

AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-ap-northeast-2}"
AWS_PROFILE="${AWS_PROFILE:-${aws_profile:-}}"
GRAFANA_URL="${GRAFANA_URL:?GRAFANA_URL is required}"
GRAFANA_ADMIN_PARAM_NAME="${GRAFANA_ADMIN_PARAM_NAME:-/donmoa/dev/grafana/admin-password}"
FOLDER_TITLE="${FOLDER_TITLE:-DonMoa}"
FOLDER_UID="${FOLDER_UID:-donmoa}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

PASSWORD="$(env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
  aws ssm get-parameter \
  --name "${GRAFANA_ADMIN_PARAM_NAME}" \
  --with-decryption \
  --query 'Parameter.Value' \
  --output text)"

LOKI_UID="$(curl -sS -u "admin:${PASSWORD}" "${GRAFANA_URL}/api/datasources/name/Loki" | jq -r '.uid')"
if [[ -z "${LOKI_UID}" || "${LOKI_UID}" == "null" ]]; then
  echo "[ERROR] Loki datasource not found in Grafana" >&2
  exit 1
fi

curl -sS -u "admin:${PASSWORD}" \
  -H 'Content-Type: application/json' \
  -X POST \
  "${GRAFANA_URL}/api/folders" \
  -d "{\"uid\":\"${FOLDER_UID}\",\"title\":\"${FOLDER_TITLE}\"}" >/tmp/grafana-folder-response.json || true

FOLDER_RESPONSE="$(cat /tmp/grafana-folder-response.json)"
if echo "${FOLDER_RESPONSE}" | jq -e '.status == "success" or .uid != null' >/dev/null 2>&1; then
  :
else
  # Folder may already exist.
  curl -sS -u "admin:${PASSWORD}" "${GRAFANA_URL}/api/folders/${FOLDER_UID}" >/tmp/grafana-folder-response.json
fi

HOME_DASHBOARD_UID=""

for dashboard_file in \
  "${ROOT_DIR}/deploy/observability/grafana-donmoa-observability-dashboard.json" \
  "${ROOT_DIR}/deploy/observability/grafana-donmoa-request-trace-dashboard.json"; do
  DASHBOARD_JSON="$(sed "s/__LOKI_UID__/${LOKI_UID}/g" "${dashboard_file}")"
  PAYLOAD="$(jq -n \
    --argjson dashboard "${DASHBOARD_JSON}" \
    --arg folderUid "${FOLDER_UID}" \
    '{dashboard:$dashboard, folderUid:$folderUid, overwrite:true}')"

  curl -sS -u "admin:${PASSWORD}" \
    -H 'Content-Type: application/json' \
    -X POST \
    "${GRAFANA_URL}/api/dashboards/db" \
    -d "${PAYLOAD}" >/tmp/grafana-dashboard-response.json

  DASHBOARD_UID="$(jq -r '.uid' /tmp/grafana-dashboard-response.json)"
  if [[ -z "${DASHBOARD_UID}" || "${DASHBOARD_UID}" == "null" ]]; then
    echo "[ERROR] dashboard provisioning failed for ${dashboard_file}" >&2
    cat /tmp/grafana-dashboard-response.json >&2
    exit 1
  fi

  if [[ "$(basename "${dashboard_file}")" == "grafana-donmoa-observability-dashboard.json" ]]; then
    HOME_DASHBOARD_UID="${DASHBOARD_UID}"
  fi
done

if [[ -z "${HOME_DASHBOARD_UID}" ]]; then
  echo "[ERROR] home dashboard uid is empty" >&2
  exit 1
fi

curl -sS -u "admin:${PASSWORD}" \
  -H 'Content-Type: application/json' \
  -X PUT \
  "${GRAFANA_URL}/api/org/preferences" \
  -d "{\"homeDashboardUID\":\"${HOME_DASHBOARD_UID}\",\"theme\":\"\",\"timezone\":\"browser\"}" >/tmp/grafana-org-preferences.json

echo "FOLDER_UID=${FOLDER_UID}"
echo "HOME_DASHBOARD_UID=${HOME_DASHBOARD_UID}"
