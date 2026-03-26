#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SYSTEM_NAMESPACE="${SYSTEM_NAMESPACE:-donmoa-system}"
AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-ap-northeast-2}"
AWS_PROFILE="${AWS_PROFILE:-${aws_profile:-}}"

LOKI_CHART_VERSION="${LOKI_CHART_VERSION:-6.55.0}"
ALLOY_CHART_VERSION="${ALLOY_CHART_VERSION:-1.6.2}"

terraform_output() {
  local module_dir="$1"
  local output_name="$2"

  if ! command -v terraform >/dev/null 2>&1; then
    return 1
  fi

  terraform -chdir="${ROOT_DIR}/${module_dir}" output -raw "${output_name}" 2>/dev/null
}

if [[ -z "${EKS_CLUSTER_NAME:-}" ]]; then
  EKS_CLUSTER_NAME="$(terraform_output "infra/terraform/eks-foundation" "cluster_name" || true)"
fi

if [[ -z "${EKS_CLUSTER_NAME:-}" ]]; then
  echo "[ERROR] EKS_CLUSTER_NAME is required" >&2
  exit 1
fi

if [[ -z "${LOKI_S3_BUCKET:-}" ]]; then
  echo "[ERROR] LOKI_S3_BUCKET is required" >&2
  exit 1
fi

if [[ -z "${LOKI_S3_ROLE_ARN:-}" ]]; then
  echo "[ERROR] LOKI_S3_ROLE_ARN is required" >&2
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
helm repo add grafana https://grafana.github.io/helm-charts >/dev/null 2>&1 || true
helm repo update >/dev/null

echo "[INFO] ensuring namespace ${SYSTEM_NAMESPACE}"
kubectl get namespace "${SYSTEM_NAMESPACE}" >/dev/null 2>&1 || kubectl create namespace "${SYSTEM_NAMESPACE}"

echo "[INFO] installing loki"
helm upgrade --install loki grafana/loki \
  --namespace "${SYSTEM_NAMESPACE}" \
  --version "${LOKI_CHART_VERSION}" \
  --values "${ROOT_DIR}/deploy/helm/addons/loki-values.yaml" \
  --set-string loki.storage.bucketNames.chunks="${LOKI_S3_BUCKET}" \
  --set-string loki.storage.bucketNames.ruler="${LOKI_S3_BUCKET}" \
  --set-string loki.storage.bucketNames.admin="${LOKI_S3_BUCKET}" \
  --set-string loki.storage.s3.region="${AWS_DEFAULT_REGION}" \
  --set-string serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn="${LOKI_S3_ROLE_ARN}" \
  --wait \
  --timeout 10m

kubectl rollout status statefulset/loki \
  --namespace "${SYSTEM_NAMESPACE}" \
  --timeout 300s

echo "[INFO] installing alloy-logs"
helm upgrade --install alloy-logs grafana/alloy \
  --namespace "${SYSTEM_NAMESPACE}" \
  --version "${ALLOY_CHART_VERSION}" \
  --values "${ROOT_DIR}/deploy/helm/addons/alloy-values.yaml" \
  --wait \
  --timeout 10m

kubectl rollout status deployment/alloy-logs \
  --namespace "${SYSTEM_NAMESPACE}" \
  --timeout 300s

echo "[INFO] observability stack is ready"
