#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PG_CONTAINER="${PG_CONTAINER:-project03-postgres}"
KEEP_REHEARSAL_DB="${KEEP_REHEARSAL_DB:-false}"
TS="$(date +%Y%m%d_%H%M%S)"
RUN_DIR="/tmp/migration_rehearsal_${TS}"
mkdir -p "$RUN_DIR"

PRODUCT_SRC_DB="product_db"
MEDIA_SRC_DB="media_db"
SEARCH_SRC_DB="search_db"

PRODUCT_REHEARSAL_DB="${PRODUCT_SRC_DB}_rehearsal_${TS}"
MEDIA_REHEARSAL_DB="${MEDIA_SRC_DB}_rehearsal_${TS}"
SEARCH_REHEARSAL_DB="${SEARCH_SRC_DB}_rehearsal_${TS}"

PRODUCT_DUMP="${RUN_DIR}/${PRODUCT_SRC_DB}.sql"
MEDIA_DUMP="${RUN_DIR}/${MEDIA_SRC_DB}.sql"
SEARCH_DUMP="${RUN_DIR}/${SEARCH_SRC_DB}.sql"
MIGRATION_SQL="${RUN_DIR}/rehearsal-migration.sql"

PASSES=0
FAILURES=0

pass() {
  PASSES=$((PASSES + 1))
  echo "[PASS] $1"
}

fail() {
  FAILURES=$((FAILURES + 1))
  echo "[FAIL] $1"
}

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "[ERR] required command not found: $cmd" >&2
    exit 1
  fi
}

psql_exec() {
  local db="$1"
  local sql="$2"
  docker exec -i "$PG_CONTAINER" psql -U postgres -d "$db" -v ON_ERROR_STOP=1 -c "$sql" >/dev/null
}

psql_query() {
  local db="$1"
  local sql="$2"
  docker exec -i "$PG_CONTAINER" psql -U postgres -d "$db" -At -c "$sql"
}

dump_db() {
  local db="$1"
  local dump_file="$2"
  docker exec -i "$PG_CONTAINER" pg_dump -U postgres -d "$db" --no-owner --no-privileges > "$dump_file"
  pass "snapshot dump created (${db})"
}

create_rehearsal_db() {
  local db="$1"
  psql_exec postgres "DROP DATABASE IF EXISTS \"${db}\";"
  psql_exec postgres "CREATE DATABASE \"${db}\";"
  pass "rehearsal DB prepared (${db})"
}

restore_dump() {
  local db="$1"
  local dump_file="$2"
  docker exec -i "$PG_CONTAINER" psql -U postgres -d "$db" -v ON_ERROR_STOP=1 < "$dump_file" >/dev/null
  pass "snapshot restored (${db})"
}

build_rehearsal_migration_sql() {
  cat > "$MIGRATION_SQL" <<SQL
\set ON_ERROR_STOP on

\echo [rehearsal] product db migration
\connect ${PRODUCT_REHEARSAL_DB}

DO \$\$
BEGIN
    IF to_regclass('public.items') IS NULL THEN
        RAISE NOTICE 'items table not found, skip thumbnail_url drop';
    ELSE
        ALTER TABLE items
            DROP COLUMN IF EXISTS thumbnail_url;
    END IF;
END
\$\$;

DO \$\$
BEGIN
    IF to_regclass('public.item_images') IS NULL THEN
        RAISE NOTICE 'item_images table not found, skip image_url drop';
    ELSE
        ALTER TABLE item_images
            DROP COLUMN IF EXISTS image_url;
    END IF;
END
\$\$;

DO \$\$
BEGIN
    IF to_regclass('public.item_media_link_sync_tasks') IS NULL THEN
        CREATE TABLE item_media_link_sync_tasks (
            id BIGINT PRIMARY KEY,
            item_id BIGINT NOT NULL,
            thumbnail_media_id BIGINT,
            gallery_media_ids VARCHAR(5000) NOT NULL DEFAULT '',
            status VARCHAR(20) NOT NULL,
            retry_count INTEGER NOT NULL DEFAULT 0,
            next_retry_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
            last_error VARCHAR(500),
            created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
            deleted_at TIMESTAMP WITHOUT TIME ZONE
        );
    ELSE
        ALTER TABLE item_media_link_sync_tasks
            ADD COLUMN IF NOT EXISTS item_id BIGINT,
            ADD COLUMN IF NOT EXISTS thumbnail_media_id BIGINT,
            ADD COLUMN IF NOT EXISTS gallery_media_ids VARCHAR(5000),
            ADD COLUMN IF NOT EXISTS status VARCHAR(20),
            ADD COLUMN IF NOT EXISTS retry_count INTEGER,
            ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP WITHOUT TIME ZONE,
            ADD COLUMN IF NOT EXISTS last_error VARCHAR(500),
            ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITHOUT TIME ZONE,
            ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITHOUT TIME ZONE,
            ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITHOUT TIME ZONE;
    END IF;

    UPDATE item_media_link_sync_tasks
    SET gallery_media_ids = ''
    WHERE gallery_media_ids IS NULL;

    UPDATE item_media_link_sync_tasks
    SET status = 'PENDING'
    WHERE status IS NULL OR status = '';

    UPDATE item_media_link_sync_tasks
    SET retry_count = 0
    WHERE retry_count IS NULL;

    UPDATE item_media_link_sync_tasks
    SET next_retry_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP)
    WHERE next_retry_at IS NULL;

    UPDATE item_media_link_sync_tasks
    SET created_at = CURRENT_TIMESTAMP
    WHERE created_at IS NULL;

    UPDATE item_media_link_sync_tasks
    SET updated_at = created_at
    WHERE updated_at IS NULL;

    ALTER TABLE item_media_link_sync_tasks
        ALTER COLUMN item_id SET NOT NULL,
        ALTER COLUMN gallery_media_ids SET DEFAULT '',
        ALTER COLUMN gallery_media_ids SET NOT NULL,
        ALTER COLUMN status SET NOT NULL,
        ALTER COLUMN retry_count SET DEFAULT 0,
        ALTER COLUMN retry_count SET NOT NULL,
        ALTER COLUMN next_retry_at SET DEFAULT CURRENT_TIMESTAMP,
        ALTER COLUMN next_retry_at SET NOT NULL,
        ALTER COLUMN created_at SET NOT NULL,
        ALTER COLUMN updated_at SET NOT NULL;

    CREATE UNIQUE INDEX IF NOT EXISTS uk_item_media_link_sync_tasks_item
        ON item_media_link_sync_tasks (item_id);

    CREATE INDEX IF NOT EXISTS idx_item_media_link_sync_tasks_status_next
        ON item_media_link_sync_tasks (status, next_retry_at);

    CREATE INDEX IF NOT EXISTS idx_item_media_link_sync_tasks_status_updated
        ON item_media_link_sync_tasks (status, updated_at);
END
\$\$;

DO \$\$
BEGIN
    IF to_regclass('public.item_images') IS NULL THEN
        RAISE NOTICE 'item_images table not found, skip';
    ELSE
        CREATE UNIQUE INDEX IF NOT EXISTS uk_item_images_item_sort_active
            ON item_images (item_id, sort_order)
            WHERE deleted_at IS NULL;
    END IF;
END
\$\$;

\echo [rehearsal] media db migration
\connect ${MEDIA_REHEARSAL_DB}

DO \$\$
BEGIN
    IF to_regclass('public.media_links') IS NULL THEN
        RAISE NOTICE 'media_links table not found, skip';
    ELSE
        CREATE UNIQUE INDEX IF NOT EXISTS uk_media_links_owner_media_active
            ON media_links (owner_type, owner_id, media_id)
            WHERE deleted_at IS NULL;

        CREATE UNIQUE INDEX IF NOT EXISTS uk_media_links_owner_thumbnail_single_active
            ON media_links (owner_type, owner_id)
            WHERE deleted_at IS NULL AND usage_type = 'THUMBNAIL';

        CREATE UNIQUE INDEX IF NOT EXISTS uk_media_links_owner_usage_sort_active
            ON media_links (owner_type, owner_id, usage_type, sort_order)
            WHERE deleted_at IS NULL;
    END IF;
END
\$\$;

\echo [rehearsal] search db migration
\connect ${SEARCH_REHEARSAL_DB}

DO \$\$
BEGIN
    IF to_regclass('public.search_thumbnail_enrichment_tasks') IS NULL THEN
        CREATE TABLE search_thumbnail_enrichment_tasks (
            id BIGSERIAL PRIMARY KEY,
            item_id BIGINT NOT NULL,
            thumbnail_media_id BIGINT NOT NULL,
            media_version BIGINT NOT NULL,
            status VARCHAR(20) NOT NULL,
            retry_count INTEGER NOT NULL DEFAULT 0,
            next_retry_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
            last_error VARCHAR(500),
            created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
            deleted_at TIMESTAMP WITHOUT TIME ZONE
        );
    ELSE
        ALTER TABLE search_thumbnail_enrichment_tasks
            ADD COLUMN IF NOT EXISTS item_id BIGINT,
            ADD COLUMN IF NOT EXISTS thumbnail_media_id BIGINT,
            ADD COLUMN IF NOT EXISTS media_version BIGINT,
            ADD COLUMN IF NOT EXISTS status VARCHAR(20),
            ADD COLUMN IF NOT EXISTS retry_count INTEGER,
            ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP WITHOUT TIME ZONE,
            ADD COLUMN IF NOT EXISTS last_error VARCHAR(500),
            ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITHOUT TIME ZONE,
            ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITHOUT TIME ZONE,
            ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITHOUT TIME ZONE;
    END IF;

    UPDATE search_thumbnail_enrichment_tasks
    SET status = 'PENDING'
    WHERE status IS NULL OR status = '';

    UPDATE search_thumbnail_enrichment_tasks
    SET retry_count = 0
    WHERE retry_count IS NULL;

    UPDATE search_thumbnail_enrichment_tasks
    SET next_retry_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP)
    WHERE next_retry_at IS NULL;

    UPDATE search_thumbnail_enrichment_tasks
    SET created_at = CURRENT_TIMESTAMP
    WHERE created_at IS NULL;

    UPDATE search_thumbnail_enrichment_tasks
    SET updated_at = created_at
    WHERE updated_at IS NULL;

    ALTER TABLE search_thumbnail_enrichment_tasks
        ALTER COLUMN item_id SET NOT NULL,
        ALTER COLUMN thumbnail_media_id SET NOT NULL,
        ALTER COLUMN media_version SET NOT NULL,
        ALTER COLUMN status SET NOT NULL,
        ALTER COLUMN retry_count SET DEFAULT 0,
        ALTER COLUMN retry_count SET NOT NULL,
        ALTER COLUMN next_retry_at SET DEFAULT CURRENT_TIMESTAMP,
        ALTER COLUMN next_retry_at SET NOT NULL,
        ALTER COLUMN created_at SET NOT NULL,
        ALTER COLUMN updated_at SET NOT NULL;

    CREATE UNIQUE INDEX IF NOT EXISTS uk_search_thumb_tasks_item
        ON search_thumbnail_enrichment_tasks (item_id);

    CREATE INDEX IF NOT EXISTS idx_search_thumb_tasks_status_next
        ON search_thumbnail_enrichment_tasks (status, next_retry_at);

    CREATE INDEX IF NOT EXISTS idx_search_thumb_tasks_status_updated
        ON search_thumbnail_enrichment_tasks (status, updated_at);
END
\$\$;

\echo [rehearsal] migration applied
SQL
  pass "rehearsal migration SQL prepared"
}

verify_product_schema() {
  local thumb_col image_col sync_table sort_index
  thumb_col="$(psql_query "$PRODUCT_REHEARSAL_DB" "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='items' AND column_name='thumbnail_url';")"
  image_col="$(psql_query "$PRODUCT_REHEARSAL_DB" "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='item_images' AND column_name='image_url';")"
  sync_table="$(psql_query "$PRODUCT_REHEARSAL_DB" "SELECT CASE WHEN to_regclass('public.item_media_link_sync_tasks') IS NULL THEN 0 ELSE 1 END;")"
  sort_index="$(psql_query "$PRODUCT_REHEARSAL_DB" "SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND tablename='item_images' AND indexname='uk_item_images_item_sort_active';")"

  [[ "$thumb_col" == "0" ]] && pass "product rehearsal: items.thumbnail_url dropped" || fail "product rehearsal: items.thumbnail_url still exists"
  [[ "$image_col" == "0" ]] && pass "product rehearsal: item_images.image_url dropped" || fail "product rehearsal: item_images.image_url still exists"
  [[ "$sync_table" == "1" ]] && pass "product rehearsal: item_media_link_sync_tasks exists" || fail "product rehearsal: item_media_link_sync_tasks missing"
  [[ "$sort_index" -ge 1 ]] && pass "product rehearsal: uk_item_images_item_sort_active exists" || fail "product rehearsal: uk_item_images_item_sort_active missing"
}

verify_media_schema() {
  local idx_owner_media idx_thumbnail_single idx_usage_sort
  idx_owner_media="$(psql_query "$MEDIA_REHEARSAL_DB" "SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND tablename='media_links' AND indexname='uk_media_links_owner_media_active';")"
  idx_thumbnail_single="$(psql_query "$MEDIA_REHEARSAL_DB" "SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND tablename='media_links' AND indexname='uk_media_links_owner_thumbnail_single_active';")"
  idx_usage_sort="$(psql_query "$MEDIA_REHEARSAL_DB" "SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND tablename='media_links' AND indexname='uk_media_links_owner_usage_sort_active';")"

  [[ "$idx_owner_media" -ge 1 ]] && pass "media rehearsal: uk_media_links_owner_media_active exists" || fail "media rehearsal: uk_media_links_owner_media_active missing"
  [[ "$idx_thumbnail_single" -ge 1 ]] && pass "media rehearsal: uk_media_links_owner_thumbnail_single_active exists" || fail "media rehearsal: uk_media_links_owner_thumbnail_single_active missing"
  [[ "$idx_usage_sort" -ge 1 ]] && pass "media rehearsal: uk_media_links_owner_usage_sort_active exists" || fail "media rehearsal: uk_media_links_owner_usage_sort_active missing"
}

verify_search_schema() {
  local task_table idx_unique idx_status_next idx_status_updated
  task_table="$(psql_query "$SEARCH_REHEARSAL_DB" "SELECT CASE WHEN to_regclass('public.search_thumbnail_enrichment_tasks') IS NULL THEN 0 ELSE 1 END;")"
  idx_unique="$(psql_query "$SEARCH_REHEARSAL_DB" "SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND tablename='search_thumbnail_enrichment_tasks' AND indexname='uk_search_thumb_tasks_item';")"
  idx_status_next="$(psql_query "$SEARCH_REHEARSAL_DB" "SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND tablename='search_thumbnail_enrichment_tasks' AND indexname='idx_search_thumb_tasks_status_next';")"
  idx_status_updated="$(psql_query "$SEARCH_REHEARSAL_DB" "SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND tablename='search_thumbnail_enrichment_tasks' AND indexname='idx_search_thumb_tasks_status_updated';")"

  [[ "$task_table" == "1" ]] && pass "search rehearsal: search_thumbnail_enrichment_tasks exists" || fail "search rehearsal: search_thumbnail_enrichment_tasks missing"
  [[ "$idx_unique" -ge 1 ]] && pass "search rehearsal: uk_search_thumb_tasks_item exists" || fail "search rehearsal: uk_search_thumb_tasks_item missing"
  [[ "$idx_status_next" -ge 1 ]] && pass "search rehearsal: idx_search_thumb_tasks_status_next exists" || fail "search rehearsal: idx_search_thumb_tasks_status_next missing"
  [[ "$idx_status_updated" -ge 1 ]] && pass "search rehearsal: idx_search_thumb_tasks_status_updated exists" || fail "search rehearsal: idx_search_thumb_tasks_status_updated missing"
}

cleanup_rehearsal_dbs() {
  if [[ "$KEEP_REHEARSAL_DB" == "true" ]]; then
    echo "[INFO] KEEP_REHEARSAL_DB=true, keeping rehearsal DBs"
    return 0
  fi
  psql_exec postgres "DROP DATABASE IF EXISTS \"${PRODUCT_REHEARSAL_DB}\";"
  psql_exec postgres "DROP DATABASE IF EXISTS \"${MEDIA_REHEARSAL_DB}\";"
  psql_exec postgres "DROP DATABASE IF EXISTS \"${SEARCH_REHEARSAL_DB}\";"
  pass "rehearsal DBs cleaned up"
}

require_cmd docker

echo "[INFO] preparing postgres container"
(cd "$ROOT/docker" && docker compose up -d postgres >/dev/null)

dump_db "$PRODUCT_SRC_DB" "$PRODUCT_DUMP"
dump_db "$MEDIA_SRC_DB" "$MEDIA_DUMP"
dump_db "$SEARCH_SRC_DB" "$SEARCH_DUMP"

create_rehearsal_db "$PRODUCT_REHEARSAL_DB"
create_rehearsal_db "$MEDIA_REHEARSAL_DB"
create_rehearsal_db "$SEARCH_REHEARSAL_DB"

restore_dump "$PRODUCT_REHEARSAL_DB" "$PRODUCT_DUMP"
restore_dump "$MEDIA_REHEARSAL_DB" "$MEDIA_DUMP"
restore_dump "$SEARCH_REHEARSAL_DB" "$SEARCH_DUMP"

build_rehearsal_migration_sql
docker exec -i "$PG_CONTAINER" psql -U postgres -d postgres -v ON_ERROR_STOP=1 < "$MIGRATION_SQL" >/dev/null
pass "rehearsal migration executed"

verify_product_schema
verify_media_schema
verify_search_schema

cleanup_rehearsal_dbs

echo "[INFO] artifacts: ${RUN_DIR}"
echo "[INFO] result passes=${PASSES} failures=${FAILURES}"
if [[ "$FAILURES" -gt 0 ]]; then
  exit 1
fi

pass "migration snapshot rehearsal verify passed"
