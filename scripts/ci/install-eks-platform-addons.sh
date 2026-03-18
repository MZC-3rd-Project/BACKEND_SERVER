#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SYSTEM_NAMESPACE="${SYSTEM_NAMESPACE:-donmoa-system}"
AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-ap-northeast-2}"
AWS_PROFILE="${AWS_PROFILE:-${aws_profile:-}}"
CLUSTER_SECRET_STORE_NAME="${CLUSTER_SECRET_STORE_NAME:-aws-secretsmanager}"

AWS_LOAD_BALANCER_CONTROLLER_CHART_VERSION="${AWS_LOAD_BALANCER_CONTROLLER_CHART_VERSION:-1.17.1}"
EXTERNAL_SECRETS_CHART_VERSION="${EXTERNAL_SECRETS_CHART_VERSION:-1.3.2}"
METRICS_SERVER_CHART_VERSION="${METRICS_SERVER_CHART_VERSION:-3.13.0}"
ARGO_ROLLOUTS_CHART_VERSION="${ARGO_ROLLOUTS_CHART_VERSION:-2.40.5}"

terraform_output() {
  local module_dir="$1"
  local output_name="$2"

  if ! command -v terraform >/dev/null 2>&1; then
    return 1
  fi

  terraform -chdir="${ROOT_DIR}/${module_dir}" output -raw "${output_name}" 2>/dev/null
}

terraform_json_output() {
  local module_dir="$1"
  local output_name="$2"

  if ! command -v terraform >/dev/null 2>&1; then
    return 1
  fi

  terraform -chdir="${ROOT_DIR}/${module_dir}" output -json "${output_name}" 2>/dev/null
}

if [[ -z "${EKS_CLUSTER_NAME:-}" ]]; then
  EKS_CLUSTER_NAME="$(terraform_output "infra/terraform/eks-foundation" "cluster_name" || true)"
fi

if [[ -z "${EKS_CLUSTER_NAME:-}" ]]; then
  echo "[ERROR] EKS_CLUSTER_NAME is required" >&2
  exit 1
fi

if [[ -z "${EKS_VPC_ID:-}" ]]; then
  EKS_VPC_ID="$(terraform_output "infra/terraform/foundation" "vpc_id" || true)"
fi

if [[ -z "${EKS_VPC_ID:-}" ]]; then
  echo "[ERROR] EKS_VPC_ID is required" >&2
  exit 1
fi

if [[ -z "${AWS_LOAD_BALANCER_CONTROLLER_ROLE_ARN:-}" ]]; then
  AWS_LOAD_BALANCER_CONTROLLER_ROLE_ARN="$(
    terraform_json_output "infra/terraform/eks-foundation" "helm_addons" \
      | ruby -rjson -e 'data = JSON.parse(STDIN.read); puts data.fetch("aws_load_balancer_controller").fetch("iam_role_arn")' \
      2>/dev/null || true
  )"
fi

if [[ -z "${EXTERNAL_SECRETS_ROLE_ARN:-}" ]]; then
  EXTERNAL_SECRETS_ROLE_ARN="$(terraform_output "infra/terraform/eks-foundation" "external_secrets_role_arn" || true)"
fi

if [[ -z "${AWS_LOAD_BALANCER_CONTROLLER_ROLE_ARN:-}" ]]; then
  echo "[ERROR] AWS_LOAD_BALANCER_CONTROLLER_ROLE_ARN is required" >&2
  exit 1
fi

if [[ -z "${EXTERNAL_SECRETS_ROLE_ARN:-}" ]]; then
  echo "[ERROR] EXTERNAL_SECRETS_ROLE_ARN is required" >&2
  exit 1
fi

if ! command -v helm >/dev/null 2>&1; then
  echo "[ERROR] helm is required" >&2
  exit 1
fi

if ! command -v kubectl >/dev/null 2>&1; then
  echo "[ERROR] kubectl is required" >&2
  exit 1
fi

echo "[INFO] ensuring Helm repositories exist"
helm repo add eks https://aws.github.io/eks-charts >/dev/null 2>&1 || true
helm repo add argo https://argoproj.github.io/argo-helm >/dev/null 2>&1 || true
helm repo add external-secrets https://charts.external-secrets.io >/dev/null 2>&1 || true
helm repo add metrics-server https://kubernetes-sigs.github.io/metrics-server/ >/dev/null 2>&1 || true
helm repo update >/dev/null

echo "[INFO] ensuring namespace ${SYSTEM_NAMESPACE}"
kubectl get namespace "${SYSTEM_NAMESPACE}" >/dev/null 2>&1 || kubectl create namespace "${SYSTEM_NAMESPACE}"

echo "[INFO] installing aws-load-balancer-controller"
helm upgrade --install aws-load-balancer-controller eks/aws-load-balancer-controller \
  --namespace "${SYSTEM_NAMESPACE}" \
  --version "${AWS_LOAD_BALANCER_CONTROLLER_CHART_VERSION}" \
  --values "${ROOT_DIR}/deploy/helm/addons/aws-load-balancer-controller-values.yaml" \
  --set-string clusterName="${EKS_CLUSTER_NAME}" \
  --set-string region="${AWS_DEFAULT_REGION}" \
  --set-string vpcId="${EKS_VPC_ID}" \
  --set-string serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn="${AWS_LOAD_BALANCER_CONTROLLER_ROLE_ARN}" \
  --wait \
  --timeout 10m

kubectl rollout status deployment/aws-load-balancer-controller \
  --namespace "${SYSTEM_NAMESPACE}" \
  --timeout 180s

echo "[INFO] installing external-secrets"
helm upgrade --install external-secrets external-secrets/external-secrets \
  --namespace "${SYSTEM_NAMESPACE}" \
  --version "${EXTERNAL_SECRETS_CHART_VERSION}" \
  --values "${ROOT_DIR}/deploy/helm/addons/external-secrets-values.yaml" \
  --set-string serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn="${EXTERNAL_SECRETS_ROLE_ARN}" \
  --timeout 10m

kubectl rollout status deployment/external-secrets \
  --namespace "${SYSTEM_NAMESPACE}" \
  --timeout 180s

kubectl rollout status deployment/external-secrets-webhook \
  --namespace "${SYSTEM_NAMESPACE}" \
  --timeout 180s

if ! kubectl rollout status deployment/external-secrets-cert-controller \
  --namespace "${SYSTEM_NAMESPACE}" \
  --timeout 60s; then
  echo "[WARN] external-secrets-cert-controller is not ready yet; continuing because the operator and webhook are available"
fi

echo "[INFO] installing metrics-server"
helm upgrade --install metrics-server metrics-server/metrics-server \
  --namespace "${SYSTEM_NAMESPACE}" \
  --version "${METRICS_SERVER_CHART_VERSION}" \
  --values "${ROOT_DIR}/deploy/helm/addons/metrics-server-values.yaml" \
  --wait \
  --timeout 10m

kubectl rollout status deployment/metrics-server \
  --namespace "${SYSTEM_NAMESPACE}" \
  --timeout 180s

echo "[INFO] installing argo-rollouts"
helm upgrade --install argo-rollouts argo/argo-rollouts \
  --namespace "${SYSTEM_NAMESPACE}" \
  --version "${ARGO_ROLLOUTS_CHART_VERSION}" \
  --values "${ROOT_DIR}/deploy/helm/addons/argo-rollouts-values.yaml" \
  --wait \
  --timeout 10m

kubectl rollout status deployment/argo-rollouts \
  --namespace "${SYSTEM_NAMESPACE}" \
  --timeout 180s

echo "[INFO] applying ClusterSecretStore/${CLUSTER_SECRET_STORE_NAME}"
cat <<EOF | kubectl apply -f -
apiVersion: external-secrets.io/v1
kind: ClusterSecretStore
metadata:
  name: ${CLUSTER_SECRET_STORE_NAME}
spec:
  provider:
    aws:
      service: SecretsManager
      region: ${AWS_DEFAULT_REGION}
EOF

echo "[INFO] platform addons are ready"
