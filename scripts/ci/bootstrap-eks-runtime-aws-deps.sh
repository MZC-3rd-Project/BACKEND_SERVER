#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-ap-northeast-2}"
AWS_PROFILE="${AWS_PROFILE:-${aws_profile:-}}"
CLUSTER_NAME="${CLUSTER_NAME:-}"
OIDC_PROVIDER_ARN="${OIDC_PROVIDER_ARN:-}"
OIDC_ISSUER_URL="${OIDC_ISSUER_URL:-}"
APP_NAMESPACE="${APP_NAMESPACE:-donmoa-dev}"

CART_TABLE_NAME="${CART_TABLE_NAME:-cart_items}"
MEDIA_BUCKET_NAME="${MEDIA_BUCKET_NAME:-team2-donmoa-media-raw}"
MEDIA_ROLE_NAME="${MEDIA_ROLE_NAME:-donmoa-dev-media-s3-role}"
CART_ROLE_NAME="${CART_ROLE_NAME:-donmoa-dev-cart-dynamodb-role}"

aws_cmd=(aws)
if [[ -n "${AWS_PROFILE}" ]]; then
  aws_cmd+=(--profile "${AWS_PROFILE}")
fi
aws_cmd+=(--region "${AWS_DEFAULT_REGION}")

terraform_output() {
  local module_dir="$1"
  local output_name="$2"

  if ! command -v terraform >/dev/null 2>&1; then
    return 1
  fi

  terraform -chdir="${ROOT_DIR}/${module_dir}" output -raw "${output_name}" 2>/dev/null
}

if [[ -z "${CLUSTER_NAME}" ]]; then
  CLUSTER_NAME="$(terraform_output "infra/terraform/eks-foundation" "cluster_name" || true)"
fi

if [[ -z "${OIDC_PROVIDER_ARN}" ]]; then
  OIDC_PROVIDER_ARN="$(terraform_output "infra/terraform/eks-foundation" "oidc_provider_arn" || true)"
fi

if [[ -z "${OIDC_ISSUER_URL}" ]]; then
  OIDC_ISSUER_URL="$(terraform_output "infra/terraform/eks-foundation" "cluster_oidc_issuer_url" || true)"
fi

if [[ -z "${CLUSTER_NAME}" || -z "${OIDC_PROVIDER_ARN}" || -z "${OIDC_ISSUER_URL}" ]]; then
  echo "[ERROR] cluster, OIDC provider ARN, and OIDC issuer URL are required" >&2
  exit 1
fi

oidc_hostpath="${OIDC_ISSUER_URL#https://}"
account_id="$("${aws_cmd[@]}" sts get-caller-identity --query Account --output text)"
cart_table_arn="arn:aws:dynamodb:${AWS_DEFAULT_REGION}:${account_id}:table/${CART_TABLE_NAME}"
media_bucket_arn="arn:aws:s3:::${MEDIA_BUCKET_NAME}"
media_object_arn="${media_bucket_arn}/*"

ensure_cart_table() {
  if "${aws_cmd[@]}" dynamodb describe-table --table-name "${CART_TABLE_NAME}" >/dev/null 2>&1; then
    echo "[INFO] DynamoDB table already exists: ${CART_TABLE_NAME}"
  else
    echo "[INFO] creating DynamoDB table: ${CART_TABLE_NAME}"
    "${aws_cmd[@]}" dynamodb create-table \
      --table-name "${CART_TABLE_NAME}" \
      --attribute-definitions \
        AttributeName=pk,AttributeType=S \
        AttributeName=sk,AttributeType=S \
      --key-schema \
        AttributeName=pk,KeyType=HASH \
        AttributeName=sk,KeyType=RANGE \
      --billing-mode PAY_PER_REQUEST >/dev/null
  fi

  "${aws_cmd[@]}" dynamodb wait table-exists --table-name "${CART_TABLE_NAME}"

  local ttl_status
  ttl_status="$("${aws_cmd[@]}" dynamodb describe-time-to-live \
    --table-name "${CART_TABLE_NAME}" \
    --query 'TimeToLiveDescription.TimeToLiveStatus' \
    --output text 2>/dev/null || true)"

  if [[ "${ttl_status}" != "ENABLED" && "${ttl_status}" != "ENABLING" ]]; then
    echo "[INFO] enabling DynamoDB TTL on expiresAtEpoch"
    "${aws_cmd[@]}" dynamodb update-time-to-live \
      --table-name "${CART_TABLE_NAME}" \
      --time-to-live-specification "Enabled=true,AttributeName=expiresAtEpoch" >/dev/null
  fi
}

upsert_role() {
  local role_name="$1"
  local trust_policy_file="$2"
  local policy_name="$3"
  local policy_file="$4"

  if "${aws_cmd[@]}" iam get-role --role-name "${role_name}" >/dev/null 2>&1; then
    echo "[INFO] updating assume-role policy for ${role_name}"
    "${aws_cmd[@]}" iam update-assume-role-policy \
      --role-name "${role_name}" \
      --policy-document "file://${trust_policy_file}" >/dev/null
  else
    echo "[INFO] creating IAM role ${role_name}"
    "${aws_cmd[@]}" iam create-role \
      --role-name "${role_name}" \
      --assume-role-policy-document "file://${trust_policy_file}" >/dev/null
  fi

  echo "[INFO] putting inline policy ${policy_name} on ${role_name}"
  "${aws_cmd[@]}" iam put-role-policy \
    --role-name "${role_name}" \
    --policy-name "${policy_name}" \
    --policy-document "file://${policy_file}" >/dev/null
}

media_trust_file="$(mktemp)"
media_policy_file="$(mktemp)"
cart_trust_file="$(mktemp)"
cart_policy_file="$(mktemp)"
trap 'rm -f "${media_trust_file}" "${media_policy_file}" "${cart_trust_file}" "${cart_policy_file}"' EXIT

cat > "${media_trust_file}" <<JSON
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
          "${oidc_hostpath}:aud": "sts.amazonaws.com",
          "${oidc_hostpath}:sub": [
            "system:serviceaccount:${APP_NAMESPACE}:media-api",
            "system:serviceaccount:${APP_NAMESPACE}:media-worker"
          ]
        }
      }
    }
  ]
}
JSON

cat > "${media_policy_file}" <<JSON
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ListMediaBucket",
      "Effect": "Allow",
      "Action": [
        "s3:ListBucket"
      ],
      "Resource": "${media_bucket_arn}"
    },
    {
      "Sid": "ManageMediaObjects",
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject",
        "s3:AbortMultipartUpload"
      ],
      "Resource": "${media_object_arn}"
    }
  ]
}
JSON

cat > "${cart_trust_file}" <<JSON
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
          "${oidc_hostpath}:aud": "sts.amazonaws.com",
          "${oidc_hostpath}:sub": "system:serviceaccount:${APP_NAMESPACE}:cart-service"
        }
      }
    }
  ]
}
JSON

cat > "${cart_policy_file}" <<JSON
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "CartTableAccess",
      "Effect": "Allow",
      "Action": [
        "dynamodb:BatchGetItem",
        "dynamodb:BatchWriteItem",
        "dynamodb:ConditionCheckItem",
        "dynamodb:DeleteItem",
        "dynamodb:DescribeTable",
        "dynamodb:GetItem",
        "dynamodb:PutItem",
        "dynamodb:Query",
        "dynamodb:Scan",
        "dynamodb:UpdateItem"
      ],
      "Resource": "${cart_table_arn}"
    }
  ]
}
JSON

ensure_cart_table
upsert_role "${MEDIA_ROLE_NAME}" "${media_trust_file}" "${MEDIA_ROLE_NAME}-policy" "${media_policy_file}"
upsert_role "${CART_ROLE_NAME}" "${cart_trust_file}" "${CART_ROLE_NAME}-policy" "${cart_policy_file}"

echo "[INFO] runtime AWS dependencies are ready"
