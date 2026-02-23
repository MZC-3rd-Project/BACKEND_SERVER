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

DO $$
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

\echo [schema-repair] notification_db: notification type checks
\connect notification_db

DO $$
BEGIN
    IF to_regclass('public.notifications') IS NULL THEN
        RAISE NOTICE 'notifications table not found, skip';
    ELSE
        ALTER TABLE notifications
            DROP CONSTRAINT IF EXISTS notifications_type_check;
        ALTER TABLE notifications
            ADD CONSTRAINT notifications_type_check
                CHECK (type IN (
                    'FUNDING_SUCCESS',
                    'FUNDING_FAIL',
                    'PAYMENT',
                    'HOTDEAL',
                    'STOCK_DEPLETED',
                    'CHAT_MESSAGE',
                    'GENERAL'
                ));
    END IF;
END
$$;

DO $$
BEGIN
    IF to_regclass('public.notification_settings') IS NULL THEN
        RAISE NOTICE 'notification_settings table not found, skip';
    ELSE
        ALTER TABLE notification_settings
            DROP CONSTRAINT IF EXISTS notification_settings_type_check;
        ALTER TABLE notification_settings
            ADD CONSTRAINT notification_settings_type_check
                CHECK (type IN (
                    'FUNDING_SUCCESS',
                    'FUNDING_FAIL',
                    'PAYMENT',
                    'HOTDEAL',
                    'STOCK_DEPLETED',
                    'CHAT_MESSAGE',
                    'GENERAL'
                ));
    END IF;
END
$$;

DO $$
BEGIN
    IF to_regclass('public.notification_templates') IS NULL THEN
        RAISE NOTICE 'notification_templates table not found, skip';
    ELSE
        ALTER TABLE notification_templates
            DROP CONSTRAINT IF EXISTS notification_templates_type_check;
        ALTER TABLE notification_templates
            ADD CONSTRAINT notification_templates_type_check
                CHECK (type IN (
                    'FUNDING_SUCCESS',
                    'FUNDING_FAIL',
                    'PAYMENT',
                    'HOTDEAL',
                    'STOCK_DEPLETED',
                    'CHAT_MESSAGE',
                    'GENERAL'
                ));
    END IF;
END
$$;

\echo [schema-repair] completed
