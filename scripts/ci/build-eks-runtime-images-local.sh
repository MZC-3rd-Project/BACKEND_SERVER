#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-ap-northeast-2}"
AWS_PROFILE="${AWS_PROFILE:-${aws_profile:-}}"
SERVICE_CATALOG_PATH="${SERVICE_CATALOG_PATH:-${ROOT_DIR}/deploy/catalog/runtime-services.yaml}"
VALUES_DIRECTORY="${VALUES_DIRECTORY:-${ROOT_DIR}/deploy/helm/environments/dev}"
EKS_DEPLOY_PLAN_PATH="${EKS_DEPLOY_PLAN_PATH:-${ROOT_DIR}/build-output/eks-deploy-plan.json}"
BUILD_PLATFORM="${BUILD_PLATFORM:-linux/amd64}"
IMAGE_TAG="${IMAGE_TAG:-localdev}"
SHORT_TAG="${SHORT_TAG:-${IMAGE_TAG}}"
JAVA_HOME="${JAVA_HOME:-}"

if [[ -z "${JAVA_HOME}" ]] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
  JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
fi

if [[ -z "${JAVA_HOME}" ]]; then
  echo "[ERROR] JAVA_HOME must point to JDK 21" >&2
  exit 1
fi

PATH="${JAVA_HOME}/bin:${PATH}"
export AWS_DEFAULT_REGION SERVICE_CATALOG_PATH VALUES_DIRECTORY EKS_DEPLOY_PLAN_PATH IMAGE_TAG SHORT_TAG JAVA_HOME PATH

aws_cmd=(aws)
if [[ -n "${AWS_PROFILE}" ]]; then
  aws_cmd+=(--profile "${AWS_PROFILE}")
fi
aws_cmd+=(--region "${AWS_DEFAULT_REGION}")

ACCOUNT_ID="${ACCOUNT_ID:-$("${aws_cmd[@]}" sts get-caller-identity --query Account --output text)}"
export ACCOUNT_ID

echo "[INFO] validating inputs"
bash "${ROOT_DIR}/scripts/ci/validate-eks-runtime-inputs.sh"

echo "[INFO] generating deploy plan"
mkdir -p "$(dirname "${EKS_DEPLOY_PLAN_PATH}")"
ruby "${ROOT_DIR}/scripts/ci/export-eks-deploy-plan.rb" --output "${EKS_DEPLOY_PLAN_PATH}"

echo "[INFO] logging into ECR"
"${aws_cmd[@]}" ecr get-login-password | docker login --username AWS --password-stdin "${ACCOUNT_ID}.dkr.ecr.${AWS_DEFAULT_REGION}.amazonaws.com"

dockerfile_path="${ROOT_DIR}/docker/eks/java-service.Dockerfile"
build_contexts=()
cleanup() {
  local path
  for path in "${build_contexts[@]:-}"; do
    [[ -n "${path}" && -d "${path}" ]] && rm -rf "${path}"
  done
}
trap cleanup EXIT

while IFS=$'\t' read -r service_key gradle_module image_repository image_tag; do
  [[ -z "${service_key}" ]] && continue

  echo "[INFO] packaging ${service_key} (${gradle_module})"
  ./gradlew ":${gradle_module}:bootJar" -x test --no-daemon

  module_path="${ROOT_DIR}/$(printf '%s' "${gradle_module}" | tr ':' '/')"
  jar_path="$(find "${module_path}/build/libs" -maxdepth 1 -type f -name '*.jar' | sort | tail -n 1)"

  if [[ -z "${jar_path}" ]]; then
    echo "[ERROR] bootJar output not found for ${service_key}" >&2
    exit 1
  fi

  echo "[INFO] building and pushing ${image_repository}:${image_tag} for ${BUILD_PLATFORM}"
  build_context="$(mktemp -d)"
  build_contexts+=("${build_context}")

  cp "${dockerfile_path}" "${build_context}/Dockerfile"
  cp "${jar_path}" "${build_context}/app.jar"

  docker buildx build \
    --platform "${BUILD_PLATFORM}" \
    --tag "${image_repository}:${image_tag}" \
    --tag "${image_repository}:latest" \
    --push \
    "${build_context}"

  rm -rf "${build_context}"
done < <(
  ruby -rjson -e '
    plan = JSON.parse(File.read(ARGV[0]))
    plan.fetch("services", []).each do |service|
      next unless service["buildType"] == "gradle"
      puts [
        service.fetch("key"),
        service.fetch("gradleModule"),
        service.fetch("imageRepository"),
        service.fetch("imageTag")
      ].join("\t")
    end
  ' "${EKS_DEPLOY_PLAN_PATH}"
)

echo "[INFO] local EKS image build completed"
