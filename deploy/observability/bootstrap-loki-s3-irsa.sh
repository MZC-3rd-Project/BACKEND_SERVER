#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-ap-northeast-2}"
AWS_PROFILE="${AWS_PROFILE:-${aws_profile:-}}"
SYSTEM_NAMESPACE="${SYSTEM_NAMESPACE:-donmoa-system}"
LOKI_SERVICE_ACCOUNT_NAME="${LOKI_SERVICE_ACCOUNT_NAME:-loki}"
LOKI_RETENTION_DAYS="${LOKI_RETENTION_DAYS:-14}"

terraform_output() {
  local module_dir="$1"
  local output_name="$2"

  if ! command -v terraform >/dev/null 2>&1; then
    return 1
  fi

  terraform -chdir="${ROOT_DIR}/${module_dir}" output -raw "${output_name}" 2>/dev/null
}

ACCOUNT_ID="$(env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" aws sts get-caller-identity --query 'Account' --output text)"

if [[ -z "${EKS_CLUSTER_NAME:-}" ]]; then
  EKS_CLUSTER_NAME="$(terraform_output "infra/terraform/eks-foundation" "cluster_name" || true)"
fi

if [[ -z "${OIDC_PROVIDER_ARN:-}" ]]; then
  OIDC_PROVIDER_ARN="$(terraform_output "infra/terraform/eks-foundation" "oidc_provider_arn" || true)"
fi

if [[ -z "${CLUSTER_OIDC_ISSUER_URL:-}" ]]; then
  CLUSTER_OIDC_ISSUER_URL="$(terraform_output "infra/terraform/eks-foundation" "cluster_oidc_issuer_url" || true)"
fi

if [[ -z "${EKS_CLUSTER_NAME:-}" ]]; then
  echo "[ERROR] EKS_CLUSTER_NAME is required" >&2
  exit 1
fi

if [[ -z "${OIDC_PROVIDER_ARN:-}" ]]; then
  echo "[ERROR] OIDC_PROVIDER_ARN is required" >&2
  exit 1
fi

if [[ -z "${CLUSTER_OIDC_ISSUER_URL:-}" ]]; then
  echo "[ERROR] CLUSTER_OIDC_ISSUER_URL is required" >&2
  exit 1
fi

if [[ -z "${LOKI_S3_BUCKET:-}" ]]; then
  LOKI_S3_BUCKET="${EKS_CLUSTER_NAME}-loki-${ACCOUNT_ID}"
fi

LOKI_S3_ROLE_NAME="${LOKI_S3_ROLE_NAME:-${EKS_CLUSTER_NAME}-loki-role}"
OIDC_ISSUER_HOSTPATH="${CLUSTER_OIDC_ISSUER_URL#https://}"
LOKI_S3_ROLE_ARN="arn:aws:iam::${ACCOUNT_ID}:role/${LOKI_S3_ROLE_NAME}"

echo "[INFO] using bucket ${LOKI_S3_BUCKET}"
echo "[INFO] using role ${LOKI_S3_ROLE_NAME}"

if ! env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" aws s3api head-bucket --bucket "${LOKI_S3_BUCKET}" >/dev/null 2>&1; then
  echo "[INFO] creating S3 bucket ${LOKI_S3_BUCKET}"
  if [[ "${AWS_DEFAULT_REGION}" == "us-east-1" ]]; then
    env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
      aws s3api create-bucket --bucket "${LOKI_S3_BUCKET}" >/dev/null
  else
    env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
      aws s3api create-bucket \
        --bucket "${LOKI_S3_BUCKET}" \
        --create-bucket-configuration "LocationConstraint=${AWS_DEFAULT_REGION}" >/dev/null
  fi
else
  echo "[INFO] S3 bucket already exists"
fi

env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
  aws s3api put-public-access-block \
  --bucket "${LOKI_S3_BUCKET}" \
  --public-access-block-configuration \
  "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true" >/dev/null

env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
  aws s3api put-bucket-encryption \
  --bucket "${LOKI_S3_BUCKET}" \
  --server-side-encryption-configuration \
  '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}' >/dev/null

env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
  aws s3api put-bucket-lifecycle-configuration \
  --bucket "${LOKI_S3_BUCKET}" \
  --lifecycle-configuration \
  "{\"Rules\":[{\"ID\":\"expire-loki-objects\",\"Status\":\"Enabled\",\"Filter\":{\"Prefix\":\"\"},\"Expiration\":{\"Days\":${LOKI_RETENTION_DAYS}}}]}" >/dev/null

TRUST_POLICY_FILE="$(mktemp)"
POLICY_FILE="$(mktemp)"
cleanup() {
  rm -f "${TRUST_POLICY_FILE}" "${POLICY_FILE}"
}
trap cleanup EXIT

cat > "${TRUST_POLICY_FILE}" <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "${OIDC_PROVIDER_ARN}"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "${OIDC_ISSUER_HOSTPATH}:aud": "sts.amazonaws.com",
          "${OIDC_ISSUER_HOSTPATH}:sub": "system:serviceaccount:${SYSTEM_NAMESPACE}:${LOKI_SERVICE_ACCOUNT_NAME}"
        }
      }
    }
  ]
}
EOF

cat > "${POLICY_FILE}" <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ListBucket",
      "Effect": "Allow",
      "Action": [
        "s3:ListBucket"
      ],
      "Resource": "arn:aws:s3:::${LOKI_S3_BUCKET}"
    },
    {
      "Sid": "ObjectAccess",
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject",
        "s3:AbortMultipartUpload",
        "s3:ListBucketMultipartUploads"
      ],
      "Resource": "arn:aws:s3:::${LOKI_S3_BUCKET}/*"
    }
  ]
}
EOF

if env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" aws iam get-role --role-name "${LOKI_S3_ROLE_NAME}" >/dev/null 2>&1; then
  echo "[INFO] IAM role already exists, updating trust policy"
  env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
    aws iam update-assume-role-policy \
    --role-name "${LOKI_S3_ROLE_NAME}" \
    --policy-document "file://${TRUST_POLICY_FILE}" >/dev/null
else
  echo "[INFO] creating IAM role ${LOKI_S3_ROLE_NAME}"
  env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
    aws iam create-role \
    --role-name "${LOKI_S3_ROLE_NAME}" \
    --assume-role-policy-document "file://${TRUST_POLICY_FILE}" >/dev/null
fi

env AWS_PROFILE="${AWS_PROFILE}" AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
  aws iam put-role-policy \
  --role-name "${LOKI_S3_ROLE_NAME}" \
  --policy-name "${LOKI_S3_ROLE_NAME}-s3-access" \
  --policy-document "file://${POLICY_FILE}" >/dev/null

echo "[INFO] Loki S3 bucket and IRSA role are ready"
echo "LOKI_S3_BUCKET=${LOKI_S3_BUCKET}"
echo "LOKI_S3_ROLE_ARN=${LOKI_S3_ROLE_ARN}"
