#!/usr/bin/env bash
set -euo pipefail

AWS_PROFILE_NAME="${AWS_PROFILE:-mzc}"
AWS_REGION_NAME="${AWS_REGION:-ap-northeast-2}"
MEDIA_BUCKET="${MEDIA_BUCKET:-team2-donmoa-media-raw}"
MEDIA_PREFIX="${MEDIA_PREFIX:-team2-donmoa-media}"
OAC_NAME="${OAC_NAME:-team2-donmoa-media-oac}"
DISTRIBUTION_COMMENT="${DISTRIBUTION_COMMENT:-team2-donmoa-media}"
PRICE_CLASS="${PRICE_CLASS:-PriceClass_200}"

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "[ERROR] required command not found: $cmd" >&2
    exit 1
  fi
}

require_cmd aws
require_cmd jq

run_aws() {
  env AWS_PROFILE="$AWS_PROFILE_NAME" AWS_REGION="$AWS_REGION_NAME" aws "$@"
}

retry() {
  local max="$1"
  shift
  local n=1
  while true; do
    if "$@"; then
      return 0
    fi
    if [[ "$n" -ge "$max" ]]; then
      return 1
    fi
    n=$((n + 1))
    sleep 2
  done
}

create_bucket_once() {
  local listed
  listed="$(run_aws s3api list-buckets \
    --query "Buckets[?Name=='${MEDIA_BUCKET}'].Name | [0]" \
    --output text 2>/dev/null || echo "__LIST_ERROR__")"
  if [[ "$listed" == "$MEDIA_BUCKET" ]]; then
    echo "[INFO] bucket exists: $MEDIA_BUCKET"
    return 0
  fi
  if [[ "$listed" == "__LIST_ERROR__" ]]; then
    echo "[WARN] list-buckets failed. assuming bucket already exists: $MEDIA_BUCKET"
    return 0
  fi

  local output
  local rc
  if [[ "$AWS_REGION_NAME" == "us-east-1" ]]; then
    set +e
    output="$(run_aws s3api create-bucket \
      --bucket "$MEDIA_BUCKET" \
      --object-ownership BucketOwnerEnforced 2>&1)"
    rc=$?
    set -e
  else
    set +e
    output="$(run_aws s3api create-bucket \
      --bucket "$MEDIA_BUCKET" \
      --create-bucket-configuration "LocationConstraint=${AWS_REGION_NAME}" \
      --object-ownership BucketOwnerEnforced 2>&1)"
    rc=$?
    set -e
  fi

  if [[ "$rc" -eq 0 ]]; then
    echo "[INFO] bucket created: $MEDIA_BUCKET"
    return 0
  fi

  if echo "$output" | grep -Eq "BucketAlreadyOwnedByYou|BucketAlreadyExists"; then
    echo "[INFO] bucket exists: $MEDIA_BUCKET"
    return 0
  fi

  echo "$output" >&2
  return 1
}

echo "[INFO] aws profile=$AWS_PROFILE_NAME region=$AWS_REGION_NAME"
ACCOUNT_ID="$(run_aws sts get-caller-identity --query Account --output text 2>/dev/null || true)"
if [[ -z "$ACCOUNT_ID" || "$ACCOUNT_ID" == "None" ]]; then
  ACCOUNT_ID="$(env AWS_PROFILE="$AWS_PROFILE_NAME" aws --region us-east-1 sts get-caller-identity --query Account --output text 2>/dev/null || true)"
fi
if [[ -z "$ACCOUNT_ID" || "$ACCOUNT_ID" == "None" ]]; then
  ACCOUNT_ID="unknown"
fi
echo "[INFO] aws account=$ACCOUNT_ID"

echo "[INFO] ensuring bucket exists: $MEDIA_BUCKET"
retry 5 create_bucket_once

echo "[INFO] applying bucket security baseline"
retry 5 run_aws s3api put-public-access-block \
  --bucket "$MEDIA_BUCKET" \
  --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true

retry 5 run_aws s3api put-bucket-encryption \
  --bucket "$MEDIA_BUCKET" \
  --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'

retry 5 run_aws s3api put-bucket-versioning \
  --bucket "$MEDIA_BUCKET" \
  --versioning-configuration Status=Enabled

CORS_FILE="$(mktemp)"
cat >"$CORS_FILE" <<'JSON'
{
  "CORSRules": [
    {
      "AllowedOrigins": ["*"],
      "AllowedMethods": ["PUT", "GET", "HEAD"],
      "AllowedHeaders": ["*"],
      "ExposeHeaders": ["ETag", "x-amz-request-id"],
      "MaxAgeSeconds": 3000
    }
  ]
}
JSON
retry 5 run_aws s3api put-bucket-cors \
  --bucket "$MEDIA_BUCKET" \
  --cors-configuration "file://$CORS_FILE"

LIFECYCLE_FILE="$(mktemp)"
cat >"$LIFECYCLE_FILE" <<JSON
{
  "Rules": [
    {
      "ID": "ExpireTempPrefix",
      "Status": "Enabled",
      "Filter": {
        "Prefix": "${MEDIA_PREFIX}/temp/"
      },
      "Expiration": {
        "Days": 3
      },
      "AbortIncompleteMultipartUpload": {
        "DaysAfterInitiation": 1
      }
    },
    {
      "ID": "TransitionObjectsToIA",
      "Status": "Enabled",
      "Filter": {
        "Prefix": "${MEDIA_PREFIX}/"
      },
      "Transitions": [
        {
          "Days": 30,
          "StorageClass": "STANDARD_IA"
        }
      ],
      "NoncurrentVersionExpiration": {
        "NoncurrentDays": 30
      }
    }
  ]
}
JSON
retry 5 run_aws s3api put-bucket-lifecycle-configuration \
  --bucket "$MEDIA_BUCKET" \
  --lifecycle-configuration "file://$LIFECYCLE_FILE"

echo "[INFO] ensuring cloudfront origin access control"
OAC_ID="$(run_aws cloudfront list-origin-access-controls \
  --query "OriginAccessControlList.Items[?Name=='${OAC_NAME}'].Id | [0]" \
  --output text)"
if [[ -z "$OAC_ID" || "$OAC_ID" == "None" ]]; then
  OAC_ID="$(run_aws cloudfront create-origin-access-control \
    --origin-access-control-config "{\"Name\":\"${OAC_NAME}\",\"Description\":\"${DISTRIBUTION_COMMENT} oac\",\"SigningProtocol\":\"sigv4\",\"SigningBehavior\":\"always\",\"OriginAccessControlOriginType\":\"s3\"}" \
    | jq -r '.OriginAccessControl.Id')"
  echo "[INFO] created oac: $OAC_ID"
else
  echo "[INFO] existing oac: $OAC_ID"
fi

echo "[INFO] ensuring cloudfront distribution"
DISTRIBUTION_ID="$(run_aws cloudfront list-distributions \
  --query "DistributionList.Items[?Comment=='${DISTRIBUTION_COMMENT}'].Id | [0]" \
  --output text)"

if [[ -z "$DISTRIBUTION_ID" || "$DISTRIBUTION_ID" == "None" ]]; then
  CALLER_REFERENCE="${DISTRIBUTION_COMMENT}-$(date +%s)"
  DIST_FILE="$(mktemp)"
  cat >"$DIST_FILE" <<JSON
{
  "CallerReference": "${CALLER_REFERENCE}",
  "Comment": "${DISTRIBUTION_COMMENT}",
  "Enabled": true,
  "Origins": {
    "Quantity": 1,
    "Items": [
      {
        "Id": "s3-${MEDIA_BUCKET}",
        "DomainName": "${MEDIA_BUCKET}.s3.${AWS_REGION_NAME}.amazonaws.com",
        "S3OriginConfig": {
          "OriginAccessIdentity": ""
        },
        "OriginAccessControlId": "${OAC_ID}"
      }
    ]
  },
  "DefaultCacheBehavior": {
    "TargetOriginId": "s3-${MEDIA_BUCKET}",
    "ViewerProtocolPolicy": "redirect-to-https",
    "AllowedMethods": {
      "Quantity": 2,
      "Items": ["GET", "HEAD"],
      "CachedMethods": {
        "Quantity": 2,
        "Items": ["GET", "HEAD"]
      }
    },
    "Compress": true,
    "ForwardedValues": {
      "QueryString": false,
      "Cookies": {
        "Forward": "none"
      }
    },
    "TrustedSigners": {
      "Enabled": false,
      "Quantity": 0
    },
    "TrustedKeyGroups": {
      "Enabled": false,
      "Quantity": 0
    },
    "MinTTL": 0,
    "DefaultTTL": 86400,
    "MaxTTL": 31536000
  },
  "PriceClass": "${PRICE_CLASS}",
  "Restrictions": {
    "GeoRestriction": {
      "RestrictionType": "none",
      "Quantity": 0
    }
  },
  "ViewerCertificate": {
    "CloudFrontDefaultCertificate": true
  },
  "HttpVersion": "http2",
  "IsIPV6Enabled": true
}
JSON
  DIST_JSON="$(retry 5 run_aws cloudfront create-distribution --distribution-config "file://$DIST_FILE")"
  DISTRIBUTION_ID="$(echo "$DIST_JSON" | jq -r '.Distribution.Id')"
  DISTRIBUTION_DOMAIN="$(echo "$DIST_JSON" | jq -r '.Distribution.DomainName')"
  DISTRIBUTION_ARN="$(echo "$DIST_JSON" | jq -r '.Distribution.ARN')"
  echo "[INFO] created distribution: $DISTRIBUTION_ID ($DISTRIBUTION_DOMAIN)"
else
  DIST_JSON="$(retry 5 run_aws cloudfront get-distribution --id "$DISTRIBUTION_ID")"
  DISTRIBUTION_DOMAIN="$(echo "$DIST_JSON" | jq -r '.Distribution.DomainName')"
  DISTRIBUTION_ARN="$(echo "$DIST_JSON" | jq -r '.Distribution.ARN')"
  echo "[INFO] existing distribution: $DISTRIBUTION_ID ($DISTRIBUTION_DOMAIN)"
fi

POLICY_FILE="$(mktemp)"
cat >"$POLICY_FILE" <<JSON
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowCloudFrontReadOnly",
      "Effect": "Allow",
      "Principal": {
        "Service": "cloudfront.amazonaws.com"
      },
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::${MEDIA_BUCKET}/*",
      "Condition": {
        "StringEquals": {
          "AWS:SourceArn": "${DISTRIBUTION_ARN}"
        }
      }
    }
  ]
}
JSON
retry 5 run_aws s3api put-bucket-policy \
  --bucket "$MEDIA_BUCKET" \
  --policy "file://$POLICY_FILE"

echo "[INFO] verifying resource state"
retry 5 run_aws s3api get-bucket-location --bucket "$MEDIA_BUCKET" >/dev/null
retry 5 run_aws s3api get-public-access-block --bucket "$MEDIA_BUCKET" >/dev/null
retry 5 run_aws s3api get-bucket-encryption --bucket "$MEDIA_BUCKET" >/dev/null
retry 5 run_aws s3api get-bucket-cors --bucket "$MEDIA_BUCKET" >/dev/null
retry 5 run_aws s3api get-bucket-lifecycle-configuration --bucket "$MEDIA_BUCKET" >/dev/null
retry 5 run_aws s3api get-bucket-policy --bucket "$MEDIA_BUCKET" >/dev/null

echo
echo "[INFO] media aws bootstrap complete"
echo "export AWS_PROFILE=${AWS_PROFILE_NAME}"
echo "export MEDIA_S3_REGION=${AWS_REGION_NAME}"
echo "export MEDIA_S3_BUCKET=${MEDIA_BUCKET}"
echo "export MEDIA_S3_KEY_PREFIX=${MEDIA_PREFIX}"
echo "export MEDIA_CLOUDFRONT_DOMAIN=https://${DISTRIBUTION_DOMAIN}"
echo "export MEDIA_CLOUDFRONT_DISTRIBUTION_ID=${DISTRIBUTION_ID}"
