#!/usr/bin/env bash
set -euo pipefail

AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-ap-northeast-2}"
AWS_PROFILE="${AWS_PROFILE:-${aws_profile:-}}"
SYSTEM_NAMESPACE="${SYSTEM_NAMESPACE:-donmoa-system}"
PROMETHEUS_SERVICE_NAME="${PROMETHEUS_SERVICE_NAME:-donmoa-monitoring-prometheus}"

GRAFANA_NAME="${GRAFANA_NAME:-donmoa-dev-grafana}"
GRAFANA_SUBNET_ID="${GRAFANA_SUBNET_ID:-subnet-058c9c6c2b3e5f341}"
GRAFANA_VPC_ID="${GRAFANA_VPC_ID:-vpc-010e2d6831b080bcd}"
GRAFANA_INSTANCE_TYPE="${GRAFANA_INSTANCE_TYPE:-t2.micro}"
GRAFANA_AMI_ID="${GRAFANA_AMI_ID:-ami-0ecfdfd1c8ae01aec}"
GRAFANA_ALLOWED_CIDR="${GRAFANA_ALLOWED_CIDR:?GRAFANA_ALLOWED_CIDR is required}"
GRAFANA_ADMIN_PARAM_NAME="${GRAFANA_ADMIN_PARAM_NAME:-/donmoa/dev/grafana/admin-password}"
LOKI_PRIVATE_URL="${LOKI_PRIVATE_URL:-}"
PROMETHEUS_PRIVATE_URL="${PROMETHEUS_PRIVATE_URL:-}"

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

ensure_loki_private_url() {
  if [[ -n "${LOKI_PRIVATE_URL}" ]]; then
    return
  fi

  LOKI_PRIVATE_URL="$(resolve_load_balancer_endpoint "loki-private" "${SYSTEM_NAMESPACE}")"
}

ensure_prometheus_private_url() {
  if [[ -n "${PROMETHEUS_PRIVATE_URL}" ]]; then
    return
  fi

  PROMETHEUS_PRIVATE_URL="$(resolve_load_balancer_endpoint "${PROMETHEUS_SERVICE_NAME}" "${SYSTEM_NAMESPACE}")"
}

wait_for_instance_running() {
  local instance_id="$1"

  env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
    aws ec2 wait instance-running --instance-ids "${instance_id}"

  env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
    aws ec2 wait instance-status-ok --instance-ids "${instance_id}"
}

wait_for_ssm_online() {
  local instance_id="$1"
  local timeout_seconds="${2:-300}"
  local start_ts ping_status

  start_ts="$(date +%s)"

  while true; do
    ping_status="$(env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
      aws ssm describe-instance-information \
      --filters "Key=InstanceIds,Values=${instance_id}" \
      --query 'InstanceInformationList[0].PingStatus' \
      --output text 2>/dev/null || true)"

    if [[ "${ping_status}" == "Online" ]]; then
      return
    fi

    if (( $(date +%s) - start_ts >= timeout_seconds )); then
      echo "[ERROR] Timed out waiting for SSM connectivity on ${instance_id}" >&2
      exit 1
    fi

    sleep 5
  done
}

reconcile_grafana_runtime() {
  local instance_id="$1"
  local remote_script command_id invocation_output status
  remote_script="$(mktemp)"

  cat > "${remote_script}" <<EOF
#!/bin/bash
set -euo pipefail

sudo mkdir -p /etc/grafana/provisioning/datasources

cat <<'DATASOURCE' | sudo tee /etc/grafana/provisioning/datasources/loki.yaml >/dev/null
apiVersion: 1
datasources:
  - name: Loki
    type: loki
    access: proxy
    url: http://${LOKI_PRIVATE_URL}:3100
    isDefault: true
    editable: false
DATASOURCE

cat <<'DATASOURCE' | sudo tee /etc/grafana/provisioning/datasources/prometheus.yaml >/dev/null
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://${PROMETHEUS_PRIVATE_URL}:9090
    isDefault: false
    editable: false
    jsonData:
      httpMethod: POST
DATASOURCE

for _ in \$(seq 1 60); do
  if command -v grafana-cli >/dev/null 2>&1; then
    break
  fi
  sleep 5
done

if ! command -v grafana-cli >/dev/null 2>&1; then
  echo "[ERROR] grafana-cli is not installed yet" >&2
  exit 1
fi

ADMIN_PASSWORD=\$(aws ssm get-parameter --name "${GRAFANA_ADMIN_PARAM_NAME}" --with-decryption --query 'Parameter.Value' --output text --region "${AWS_DEFAULT_REGION}")

sudo systemctl enable grafana-server
sudo systemctl restart grafana-server
sleep 5
sudo grafana-cli admin reset-admin-password "\${ADMIN_PASSWORD}"
sudo systemctl restart grafana-server
EOF

  command_id="$(
    env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
      aws ssm send-command \
        --instance-ids "${instance_id}" \
        --document-name AWS-RunShellScript \
        --parameters "$(jq -Rn --arg cmd "$(cat "${remote_script}")" '{commands: [$cmd]}')" \
        --query 'Command.CommandId' \
        --output text
  )"

  env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
    aws ssm wait command-executed \
    --command-id "${command_id}" \
    --instance-id "${instance_id}"

  invocation_output="$(
    env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
      aws ssm get-command-invocation \
      --command-id "${command_id}" \
      --instance-id "${instance_id}" \
      --query '{status:Status,stdout:StandardOutputContent,stderr:StandardErrorContent}' \
      --output json
  )"
  status="$(printf '%s' "${invocation_output}" | jq -r '.status')"

  if [[ "${status}" != "Success" ]]; then
    printf '%s\n' "${invocation_output}" >&2
    exit 1
  fi
}

ensure_role_and_instance_profile() {
  local role_name="${GRAFANA_NAME}-ssm-role"
  local profile_name="${GRAFANA_NAME}-instance-profile"

  if ! env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
      aws iam get-role --role-name "${role_name}" >/dev/null 2>&1; then
    cat > /tmp/grafana-ec2-trust.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": { "Service": "ec2.amazonaws.com" },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF
    env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
      aws iam create-role \
      --role-name "${role_name}" \
      --assume-role-policy-document file:///tmp/grafana-ec2-trust.json >/dev/null
  fi

  env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
    aws iam attach-role-policy \
    --role-name "${role_name}" \
    --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore >/dev/null || true

  cat > /tmp/grafana-ec2-ssm-param-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["ssm:GetParameter"],
      "Resource": "arn:aws:ssm:${AWS_DEFAULT_REGION}:*:parameter${GRAFANA_ADMIN_PARAM_NAME}"
    }
  ]
}
EOF
  env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
    aws iam put-role-policy \
    --role-name "${role_name}" \
    --policy-name "${role_name}-ssm-parameter" \
    --policy-document file:///tmp/grafana-ec2-ssm-param-policy.json >/dev/null

  if ! env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
      aws iam get-instance-profile --instance-profile-name "${profile_name}" >/dev/null 2>&1; then
    env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
      aws iam create-instance-profile --instance-profile-name "${profile_name}" >/dev/null
    sleep 5
  fi

  if ! env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
      aws iam get-instance-profile --instance-profile-name "${profile_name}" \
      --query "InstanceProfile.Roles[?RoleName=='${role_name}'].RoleName" --output text | grep -q "${role_name}"; then
    env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
      aws iam add-role-to-instance-profile \
      --instance-profile-name "${profile_name}" \
      --role-name "${role_name}" >/dev/null || true
    sleep 10
  fi

  echo "${profile_name}"
}

ensure_security_group() {
  local sg_id
  sg_id="$(env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
    aws ec2 describe-security-groups \
    --filters "Name=vpc-id,Values=${GRAFANA_VPC_ID}" "Name=group-name,Values=${GRAFANA_NAME}-sg" \
    --query 'SecurityGroups[0].GroupId' --output text)"

  if [[ "${sg_id}" == "None" || -z "${sg_id}" ]]; then
    sg_id="$(env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
      aws ec2 create-security-group \
      --group-name "${GRAFANA_NAME}-sg" \
      --description "Grafana EC2 security group" \
      --vpc-id "${GRAFANA_VPC_ID}" \
      --query 'GroupId' --output text)"
  fi

  env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
    aws ec2 authorize-security-group-ingress \
    --group-id "${sg_id}" \
    --ip-permissions "IpProtocol=tcp,FromPort=3000,ToPort=3000,IpRanges=[{CidrIp=${GRAFANA_ALLOWED_CIDR},Description='Grafana UI'}]" >/dev/null 2>&1 || true

  echo "${sg_id}"
}

ensure_admin_password() {
  if env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
      aws ssm get-parameter --name "${GRAFANA_ADMIN_PARAM_NAME}" --with-decryption >/dev/null 2>&1; then
    return
  fi

  local password
  password="$(openssl rand -base64 24 | tr -d '\n' | tr '/+' 'AZ')"
  env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
    aws ssm put-parameter \
    --name "${GRAFANA_ADMIN_PARAM_NAME}" \
    --type SecureString \
    --overwrite \
    --value "${password}" >/dev/null
}

launch_instance() {
  local profile_name="$1"
  local sg_id="$2"
  local existing_instance_id existing_instance_state new_instance_id

  existing_instance_id="$(env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
    aws ec2 describe-instances \
    --filters "Name=tag:Name,Values=${GRAFANA_NAME}" "Name=instance-state-name,Values=pending,running,stopping,stopped" \
    --query 'Reservations[0].Instances[0].InstanceId' --output text)"

  if [[ "${existing_instance_id}" != "None" && -n "${existing_instance_id}" ]]; then
    existing_instance_state="$(env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
      aws ec2 describe-instances \
      --instance-ids "${existing_instance_id}" \
      --query 'Reservations[0].Instances[0].State.Name' \
      --output text)"

    if [[ "${existing_instance_state}" == "stopped" ]]; then
      env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
        aws ec2 start-instances --instance-ids "${existing_instance_id}" >/dev/null
      wait_for_instance_running "${existing_instance_id}"
    elif [[ "${existing_instance_state}" == "stopping" ]]; then
      env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
        aws ec2 wait instance-stopped --instance-ids "${existing_instance_id}"
      env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
        aws ec2 start-instances --instance-ids "${existing_instance_id}" >/dev/null
      wait_for_instance_running "${existing_instance_id}"
    elif [[ "${existing_instance_state}" == "pending" ]]; then
      wait_for_instance_running "${existing_instance_id}"
    fi

    echo "${existing_instance_id}"
    return
  fi

  cat > /tmp/grafana-user-data.sh <<EOF
#!/bin/bash
set -euxo pipefail

cat >/etc/yum.repos.d/grafana.repo <<'REPO'
[grafana]
name=grafana
baseurl=https://rpm.grafana.com
repo_gpgcheck=1
enabled=1
gpgcheck=1
gpgkey=https://rpm.grafana.com/gpg.key
sslverify=1
sslcacert=/etc/pki/tls/certs/ca-bundle.crt
REPO

dnf install -y grafana

mkdir -p /etc/grafana/provisioning/datasources

cat >/etc/grafana/provisioning/datasources/loki.yaml <<DATASOURCE
apiVersion: 1
datasources:
  - name: Loki
    type: loki
    access: proxy
    url: http://${LOKI_PRIVATE_URL}:3100
    isDefault: true
    editable: false
DATASOURCE

cat >/etc/grafana/provisioning/datasources/prometheus.yaml <<DATASOURCE
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://${PROMETHEUS_PRIVATE_URL}:9090
    isDefault: false
    editable: false
    jsonData:
      httpMethod: POST
DATASOURCE

systemctl enable grafana-server
systemctl start grafana-server
sleep 10

ADMIN_PASSWORD=\$(aws ssm get-parameter --name "${GRAFANA_ADMIN_PARAM_NAME}" --with-decryption --query 'Parameter.Value' --output text --region "${AWS_DEFAULT_REGION}")
grafana-cli admin reset-admin-password "\${ADMIN_PASSWORD}"
systemctl restart grafana-server
EOF

  new_instance_id="$(env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
    aws ec2 run-instances \
    --image-id "${GRAFANA_AMI_ID}" \
    --instance-type "${GRAFANA_INSTANCE_TYPE}" \
    --iam-instance-profile "Name=${profile_name}" \
    --security-group-ids "${sg_id}" \
    --subnet-id "${GRAFANA_SUBNET_ID}" \
    --associate-public-ip-address \
    --metadata-options "HttpTokens=required,HttpEndpoint=enabled" \
    --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=${GRAFANA_NAME}},{Key=Project,Value=donmoa},{Key=Environment,Value=dev},{Key=Role,Value=grafana}]" \
    --user-data file:///tmp/grafana-user-data.sh \
    --query 'Instances[0].InstanceId' --output text)"

  wait_for_instance_running "${new_instance_id}"

  echo "${new_instance_id}"
}

ensure_admin_password
ensure_loki_private_url
ensure_prometheus_private_url
PROFILE_NAME="$(ensure_role_and_instance_profile)"
SG_ID="$(ensure_security_group)"
INSTANCE_ID="$(launch_instance "${PROFILE_NAME}" "${SG_ID}")"
wait_for_ssm_online "${INSTANCE_ID}"
reconcile_grafana_runtime "${INSTANCE_ID}"

echo "INSTANCE_ID=${INSTANCE_ID}"
echo "SECURITY_GROUP_ID=${SG_ID}"
echo "INSTANCE_PROFILE=${PROFILE_NAME}"
echo "GRAFANA_ADMIN_PARAM_NAME=${GRAFANA_ADMIN_PARAM_NAME}"
echo "LOKI_PRIVATE_URL=${LOKI_PRIVATE_URL}"
echo "PROMETHEUS_PRIVATE_URL=${PROMETHEUS_PRIVATE_URL}"
