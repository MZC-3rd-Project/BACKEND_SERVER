#!/bin/sh
set -eu

ENDPOINT="${DYNAMODB_ENDPOINT:-http://dynamodb:8000}"
REGION="${AWS_DEFAULT_REGION:-ap-northeast-2}"
TABLE_NAME="${CART_DYNAMODB_TABLE_NAME:-donmoa-local-cart-items}"

echo "Waiting for DynamoDB Local at ${ENDPOINT}..."
until aws dynamodb list-tables \
  --endpoint-url "${ENDPOINT}" \
  --region "${REGION}" >/dev/null 2>&1; do
  sleep 1
done

if aws dynamodb describe-table \
  --table-name "${TABLE_NAME}" \
  --endpoint-url "${ENDPOINT}" \
  --region "${REGION}" >/dev/null 2>&1; then
  echo "DynamoDB table already exists: ${TABLE_NAME}"
  exit 0
fi

echo "Creating DynamoDB table: ${TABLE_NAME}"
aws dynamodb create-table \
  --table-name "${TABLE_NAME}" \
  --attribute-definitions \
    AttributeName=pk,AttributeType=S \
    AttributeName=sk,AttributeType=S \
  --key-schema \
    AttributeName=pk,KeyType=HASH \
    AttributeName=sk,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  --endpoint-url "${ENDPOINT}" \
  --region "${REGION}" >/dev/null

aws dynamodb update-time-to-live \
  --table-name "${TABLE_NAME}" \
  --time-to-live-specification "Enabled=true, AttributeName=expiresAtEpoch" \
  --endpoint-url "${ENDPOINT}" \
  --region "${REGION}" >/dev/null

echo "DynamoDB table is ready: ${TABLE_NAME}"
