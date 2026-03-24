#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CATALOG_PATH="${SERVICE_CATALOG_PATH:-${ROOT_DIR}/deploy/catalog/runtime-services.yaml}"
VALUES_DIR="${VALUES_DIRECTORY:-${ROOT_DIR}/deploy/helm/environments/dev}"
HELM_CHART_PATH="${HELM_CHART_PATH:-${ROOT_DIR}/deploy/helm/charts/spring-service}"
VALIDATION_PLAN_PATH="${EKS_DEPLOY_PLAN_PATH:-${ROOT_DIR}/build-output/eks-validation-plan.json}"
STRICT_HELM_TEMPLATE="${STRICT_HELM_TEMPLATE:-false}"

export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-ap-northeast-2}"
export ACCOUNT_ID="${ACCOUNT_ID:-000000000000}"
export SHORT_TAG="${SHORT_TAG:-validate}"

echo "[INFO] validating values inventory and deploy plan"
ruby "${ROOT_DIR}/scripts/ci/export-eks-deploy-plan.rb" \
  --catalog "${CATALOG_PATH}" \
  --values-dir "${VALUES_DIR}" \
  --output "${VALIDATION_PLAN_PATH}" >/dev/null

echo "[INFO] checking for legacy .internal endpoints"
if rg -n "\\.internal\\b" "${VALUES_DIR}"; then
  echo "[ERROR] legacy .internal endpoint detected in values files" >&2
  exit 1
fi

if command -v helm >/dev/null 2>&1; then
  echo "[INFO] rendering helm templates for all selected services"
  while IFS=$'\t' read -r release_name values_file image_repository image_tag; do
    [ -z "${release_name}" ] && continue
    helm template "${release_name}" "${HELM_CHART_PATH}" \
      --values "${ROOT_DIR}/${values_file}" \
      --set "image.repository=${image_repository}" \
      --set "image.tag=${image_tag}" >/dev/null
  done < <(
    ruby -rjson -e '
      plan = JSON.parse(File.read(ARGV[0]))
      plan.fetch("services", []).each do |service|
        puts [service.fetch("releaseName"), service.fetch("valuesFile"), service.fetch("imageRepository"), service.fetch("imageTag")].join("\t")
      end
    ' "${VALIDATION_PLAN_PATH}"
  )
else
  if [ "${STRICT_HELM_TEMPLATE}" = "true" ]; then
    echo "[ERROR] helm is required but not installed" >&2
    exit 1
  fi
  echo "[WARN] helm not found; skipping helm template validation"
fi

echo "[INFO] EKS runtime validation completed"
