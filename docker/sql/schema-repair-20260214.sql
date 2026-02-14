\set ON_ERROR_STOP on

-- Schema repair for existing local PostgreSQL volumes.
-- Run after each service has started at least once so tables exist.

\echo [schema-repair] funding_db: funding_participations + outbox_messages
\connect funding_db

DO $$
BEGIN
    IF to_regclass('public.funding_participations') IS NULL THEN
        RAISE NOTICE 'funding_participations table not found, skip';
    ELSE
        ALTER TABLE funding_participations
            ADD COLUMN IF NOT EXISTS order_id BIGINT;

        UPDATE funding_participations
        SET order_id = COALESCE(order_id, reservation_id, id)
        WHERE order_id IS NULL;

        ALTER TABLE funding_participations
            ALTER COLUMN order_id SET NOT NULL;

        CREATE INDEX IF NOT EXISTS idx_participation_order_id
            ON funding_participations (order_id);
    END IF;
END
$$;

DO $$
BEGIN
    IF to_regclass('public.outbox_messages') IS NULL THEN
        RAISE NOTICE 'outbox_messages table not found, skip';
    ELSE
        ALTER TABLE outbox_messages
            ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(255),
            ADD COLUMN IF NOT EXISTS causation_id VARCHAR(255),
            ADD COLUMN IF NOT EXISTS retry_count INTEGER;

        UPDATE outbox_messages
        SET retry_count = 0
        WHERE retry_count IS NULL;

        ALTER TABLE outbox_messages
            ALTER COLUMN retry_count SET DEFAULT 0,
            ALTER COLUMN retry_count SET NOT NULL;

        CREATE INDEX IF NOT EXISTS idx_outbox_status_created
            ON outbox_messages (status, created_at);

        CREATE INDEX IF NOT EXISTS idx_outbox_status_updated
            ON outbox_messages (status, updated_at);

        CREATE INDEX IF NOT EXISTS idx_outbox_aggregate
            ON outbox_messages (aggregate_type, aggregate_id);

        CREATE UNIQUE INDEX IF NOT EXISTS idx_outbox_event_id
            ON outbox_messages (event_id);
    END IF;
END
$$;

\echo [schema-repair] hotdeal_db: hot_deals + outbox_messages
\connect hotdeal_db

DO $$
BEGIN
    IF to_regclass('public.hot_deals') IS NULL THEN
        RAISE NOTICE 'hot_deals table not found, skip';
    ELSE
        ALTER TABLE hot_deals
            ADD COLUMN IF NOT EXISTS max_per_user INTEGER;

        UPDATE hot_deals
        SET max_per_user = 1
        WHERE max_per_user IS NULL;

        ALTER TABLE hot_deals
            ALTER COLUMN max_per_user SET DEFAULT 1,
            ALTER COLUMN max_per_user SET NOT NULL;
    END IF;
END
$$;

DO $$
BEGIN
    IF to_regclass('public.outbox_messages') IS NULL THEN
        RAISE NOTICE 'outbox_messages table not found, skip';
    ELSE
        ALTER TABLE outbox_messages
            ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(255),
            ADD COLUMN IF NOT EXISTS causation_id VARCHAR(255),
            ADD COLUMN IF NOT EXISTS retry_count INTEGER;

        UPDATE outbox_messages
        SET retry_count = 0
        WHERE retry_count IS NULL;

        ALTER TABLE outbox_messages
            ALTER COLUMN retry_count SET DEFAULT 0,
            ALTER COLUMN retry_count SET NOT NULL;

        CREATE INDEX IF NOT EXISTS idx_outbox_status_created
            ON outbox_messages (status, created_at);

        CREATE INDEX IF NOT EXISTS idx_outbox_status_updated
            ON outbox_messages (status, updated_at);

        CREATE INDEX IF NOT EXISTS idx_outbox_aggregate
            ON outbox_messages (aggregate_type, aggregate_id);

        CREATE UNIQUE INDEX IF NOT EXISTS idx_outbox_event_id
            ON outbox_messages (event_id);
    END IF;
END
$$;

\echo [schema-repair] product_db: outbox_messages
\connect product_db

DO $$
BEGIN
    IF to_regclass('public.outbox_messages') IS NULL THEN
        RAISE NOTICE 'outbox_messages table not found, skip';
    ELSE
        ALTER TABLE outbox_messages
            ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(255),
            ADD COLUMN IF NOT EXISTS causation_id VARCHAR(255),
            ADD COLUMN IF NOT EXISTS retry_count INTEGER;

        UPDATE outbox_messages
        SET retry_count = 0
        WHERE retry_count IS NULL;

        ALTER TABLE outbox_messages
            ALTER COLUMN retry_count SET DEFAULT 0,
            ALTER COLUMN retry_count SET NOT NULL;

        CREATE INDEX IF NOT EXISTS idx_outbox_status_created
            ON outbox_messages (status, created_at);

        CREATE INDEX IF NOT EXISTS idx_outbox_status_updated
            ON outbox_messages (status, updated_at);

        CREATE INDEX IF NOT EXISTS idx_outbox_aggregate
            ON outbox_messages (aggregate_type, aggregate_id);

        CREATE UNIQUE INDEX IF NOT EXISTS idx_outbox_event_id
            ON outbox_messages (event_id);
    END IF;
END
$$;

\echo [schema-repair] stock_db: outbox_messages
\connect stock_db

DO $$
BEGIN
    IF to_regclass('public.outbox_messages') IS NULL THEN
        RAISE NOTICE 'outbox_messages table not found, skip';
    ELSE
        ALTER TABLE outbox_messages
            ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(255),
            ADD COLUMN IF NOT EXISTS causation_id VARCHAR(255),
            ADD COLUMN IF NOT EXISTS retry_count INTEGER;

        UPDATE outbox_messages
        SET retry_count = 0
        WHERE retry_count IS NULL;

        ALTER TABLE outbox_messages
            ALTER COLUMN retry_count SET DEFAULT 0,
            ALTER COLUMN retry_count SET NOT NULL;

        CREATE INDEX IF NOT EXISTS idx_outbox_status_created
            ON outbox_messages (status, created_at);

        CREATE INDEX IF NOT EXISTS idx_outbox_status_updated
            ON outbox_messages (status, updated_at);

        CREATE INDEX IF NOT EXISTS idx_outbox_aggregate
            ON outbox_messages (aggregate_type, aggregate_id);

        CREATE UNIQUE INDEX IF NOT EXISTS idx_outbox_event_id
            ON outbox_messages (event_id);
    END IF;
END
$$;

\echo [schema-repair] sales_db: outbox_messages
\connect sales_db

DO $$
BEGIN
    IF to_regclass('public.outbox_messages') IS NULL THEN
        RAISE NOTICE 'outbox_messages table not found, skip';
    ELSE
        ALTER TABLE outbox_messages
            ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(255),
            ADD COLUMN IF NOT EXISTS causation_id VARCHAR(255),
            ADD COLUMN IF NOT EXISTS retry_count INTEGER;

        UPDATE outbox_messages
        SET retry_count = 0
        WHERE retry_count IS NULL;

        ALTER TABLE outbox_messages
            ALTER COLUMN retry_count SET DEFAULT 0,
            ALTER COLUMN retry_count SET NOT NULL;

        CREATE INDEX IF NOT EXISTS idx_outbox_status_created
            ON outbox_messages (status, created_at);

        CREATE INDEX IF NOT EXISTS idx_outbox_status_updated
            ON outbox_messages (status, updated_at);

        CREATE INDEX IF NOT EXISTS idx_outbox_aggregate
            ON outbox_messages (aggregate_type, aggregate_id);

        CREATE UNIQUE INDEX IF NOT EXISTS idx_outbox_event_id
            ON outbox_messages (event_id);
    END IF;
END
$$;

\echo [schema-repair] completed
