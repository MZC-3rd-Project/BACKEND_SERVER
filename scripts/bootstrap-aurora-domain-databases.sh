#!/usr/bin/env bash
set -euo pipefail

: "${AURORA_HOST:?AURORA_HOST is required}"
: "${AURORA_USER:?AURORA_USER is required}"
: "${AURORA_PASSWORD:?AURORA_PASSWORD is required}"

AURORA_PORT="${AURORA_PORT:-5432}"
BASE_DB="${AURORA_BASE_DB:-postgres}"

# Override by setting DOMAIN_DATABASES="db1,db2,db3"
if [[ -n "${DOMAIN_DATABASES:-}" ]]; then
  IFS=',' read -r -a DBS <<<"$DOMAIN_DATABASES"
else
  DBS=(
    auth_db
    profile_db
    product_db
    stock_db
    sales_db
    funding_db
    hotdeal_db
    order_db
    payment_db
    review_db
    store_db
    store_query_db
    notification_db
    chat_db
    media_db
    analytics_db
    keycloak_db
  )
fi

if ! command -v psql >/dev/null 2>&1; then
  echo "psql command not found" >&2
  exit 1
fi

echo "Bootstrap start: host=${AURORA_HOST} port=${AURORA_PORT} base_db=${BASE_DB}"
for db in "${DBS[@]}"; do
  [[ -z "$db" ]] && continue
  exists="$(PGPASSWORD="$AURORA_PASSWORD" psql -h "$AURORA_HOST" -p "$AURORA_PORT" -U "$AURORA_USER" -d "$BASE_DB" -tAc "SELECT 1 FROM pg_database WHERE datname='${db}'")"
  if [[ "$exists" == "1" ]]; then
    echo "skip: ${db} (already exists)"
    continue
  fi

  PGPASSWORD="$AURORA_PASSWORD" psql -h "$AURORA_HOST" -p "$AURORA_PORT" -U "$AURORA_USER" -d "$BASE_DB" -c "CREATE DATABASE \"${db}\";"
  echo "created: ${db}"
done

echo "Bootstrap done"
