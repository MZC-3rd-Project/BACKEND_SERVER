#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
NAMESPACE="${NAMESPACE:-donmoa-dev}"
CHART_PATH="${ROOT_DIR}/deploy/helm/charts/spring-service"

RELEASES=(
  analytics-dashboard
  auth
  cart
  chat
  client-gateway
  funding
  hot-deal
  media-api
  media-worker
  notification
  order
  payment
  product
  profile
  review
  sales
  search
  stock
  store-query
  store
)

for release in "${RELEASES[@]}"; do
  values_file="${ROOT_DIR}/deploy/helm/environments/dev/${release}.yaml"
  echo "[INFO] applying ServiceMonitor for ${release}"
  rendered="$(helm template "${release}" "${CHART_PATH}" \
    --namespace "${NAMESPACE}" \
    --values "${values_file}" \
    --show-only templates/servicemonitor.yaml 2>/dev/null || true)"

  if [[ -z "${rendered}" ]]; then
    echo "[INFO] no ServiceMonitor rendered for ${release}"
    continue
  fi

  printf '%s\n' "${rendered}" | kubectl apply -n "${NAMESPACE}" -f -
done

echo "[INFO] all service monitors applied"
