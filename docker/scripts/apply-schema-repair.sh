#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SQL_FILE="${REPO_ROOT}/docker/sql/schema-repair-20260214.sql"
POSTGRES_CONTAINER="${1:-project03-postgres}"

if [[ ! -f "${SQL_FILE}" ]]; then
  echo "[schema-repair] SQL file not found: ${SQL_FILE}" >&2
  exit 1
fi

echo "[schema-repair] applying ${SQL_FILE} -> container ${POSTGRES_CONTAINER}"
docker exec -i "${POSTGRES_CONTAINER}" psql -U postgres -d postgres < "${SQL_FILE}"
echo "[schema-repair] done"
