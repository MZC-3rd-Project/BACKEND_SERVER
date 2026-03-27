#!/usr/bin/env bash
set -euo pipefail

AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-ap-northeast-2}"
AWS_PROFILE="${AWS_PROFILE:-${aws_profile:-}}"
GRAFANA_URL="${GRAFANA_URL:?GRAFANA_URL is required}"
GRAFANA_ADMIN_PARAM_NAME="${GRAFANA_ADMIN_PARAM_NAME:-/donmoa/dev/grafana/admin-password}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GRAFANA_INSTANCE_ID="${GRAFANA_INSTANCE_ID:?GRAFANA_INSTANCE_ID is required}"
LOKI_PRIVATE_URL="${LOKI_PRIVATE_URL:?LOKI_PRIVATE_URL is required}"

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

ALERT_RULES_CONTENT="$(sed "s/__LOKI_UID__/${LOKI_UID}/g" "${ROOT_DIR}/deploy/observability/grafana-donmoa-alert-rules.yaml")"

TMP_RULES_FILE="$(mktemp)"
cat > "${TMP_RULES_FILE}" <<EOF
${ALERT_RULES_CONTENT}
EOF

REMOTE_COMMAND_FILE="$(mktemp)"
cat > "${REMOTE_COMMAND_FILE}" <<EOF
sudo mkdir -p /etc/grafana/provisioning/alerting
sudo tee /etc/grafana/provisioning/alerting/donmoa-alerts.yaml >/dev/null <<'ALERTS'
${ALERT_RULES_CONTENT}
ALERTS
sudo awk '
  BEGIN {skip=0}
  /^# BEGIN DONMOA ALERTING$/ {skip=1; next}
  /^# END DONMOA ALERTING$/ {skip=0; next}
  skip==0 {print}
' /etc/grafana/grafana.ini | sudo tee /tmp/grafana.ini.cleaned >/dev/null
cat <<'CFG' | sudo tee -a /tmp/grafana.ini.cleaned >/dev/null
# BEGIN DONMOA ALERTING
[unified_alerting.state_history]
enabled = true
backend = loki
loki_remote_url = http://${LOKI_PRIVATE_URL}:3100

[feature_toggles]
enable = alertingCentralAlertHistory
# END DONMOA ALERTING
CFG
sudo mv /tmp/grafana.ini.cleaned /etc/grafana/grafana.ini
sudo systemctl restart grafana-server
EOF

COMMAND_ID="$(
  env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
    aws ssm send-command \
      --instance-ids "${GRAFANA_INSTANCE_ID}" \
      --document-name AWS-RunShellScript \
      --parameters "$(jq -Rn --arg cmd "$(cat "${REMOTE_COMMAND_FILE}")" '{commands: [$cmd]}')" \
      --query 'Command.CommandId' \
      --output text
)"

sleep 5

env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
  aws ssm get-command-invocation \
  --command-id "${COMMAND_ID}" \
  --instance-id "${GRAFANA_INSTANCE_ID}" \
  --query '{status:Status,stdout:StandardOutputContent,stderr:StandardErrorContent}' \
  --output json
