#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SYSTEM_NAMESPACE="${SYSTEM_NAMESPACE:-donmoa-system}"
PROM_STACK_CHART_VERSION="${PROM_STACK_CHART_VERSION:-82.13.6}"

if ! command -v helm >/dev/null 2>&1; then
  echo "[ERROR] helm is required" >&2
  exit 1
fi

if ! command -v kubectl >/dev/null 2>&1; then
  echo "[ERROR] kubectl is required" >&2
  exit 1
fi

echo "[INFO] ensuring Helm repositories exist"
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null 2>&1 || true
helm repo update >/dev/null

echo "[INFO] ensuring namespace ${SYSTEM_NAMESPACE}"
kubectl get namespace "${SYSTEM_NAMESPACE}" >/dev/null 2>&1 || kubectl create namespace "${SYSTEM_NAMESPACE}"

echo "[INFO] installing kube-prometheus-stack"
helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  --namespace "${SYSTEM_NAMESPACE}" \
  --version "${PROM_STACK_CHART_VERSION}" \
  --values "${ROOT_DIR}/deploy/helm/addons/kube-prometheus-stack-values.yaml" \
  --wait \
  --timeout 15m

kubectl rollout status deployment/donmoa-monitoring-operator \
  --namespace "${SYSTEM_NAMESPACE}" \
  --timeout 300s

kubectl rollout status statefulset/prometheus-donmoa-monitoring-prometheus \
  --namespace "${SYSTEM_NAMESPACE}" \
  --timeout 600s

echo "[INFO] applying shared ServiceMonitor for donmoa-dev services"
kubectl apply -f "${ROOT_DIR}/deploy/helm/addons/donmoa-dev-services-servicemonitor.yaml"

echo "[INFO] metrics stack is ready"
