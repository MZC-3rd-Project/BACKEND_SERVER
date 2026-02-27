-- =========================================
-- Notification DB Schema (PostgreSQL)
-- =========================================

-- 1) Notifications
CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT PRIMARY KEY, -- Snowflake ID
    recipient_id BIGINT NOT NULL, -- user-service users.id (FK 미연결)
    actor_id BIGINT, -- user-service users.id (FK 미연결)
    type VARCHAR(40) NOT NULL
        CHECK (type IN ('FUNDING_SUCCESS', 'FUNDING_FAIL', 'PAYMENT', 'HOTDEAL', 'STOCK_DEPLETED', 'CHAT_MESSAGE', 'GENERAL')),
    channel VARCHAR(20) NOT NULL
        CHECK (channel IN ('IN_APP', 'EMAIL', 'SMS', 'KAKAO', 'PUSH')),
    title VARCHAR(200) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    reference_type VARCHAR(50),
    reference_id VARCHAR(100),
    external_event_id VARCHAR(100),
    dedupe_key VARCHAR(160) NOT NULL,
    payload JSONB,
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('CREATED', 'DISPATCHED', 'FAILED', 'READ', 'ARCHIVED')),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP,
    sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_read_id
    ON notifications (recipient_id, is_read, id);
CREATE INDEX IF NOT EXISTS idx_notifications_external_event_id
    ON notifications (external_event_id);
CREATE INDEX IF NOT EXISTS idx_notifications_status
    ON notifications (status);

-- soft delete 호환 unique
CREATE UNIQUE INDEX IF NOT EXISTS uq_notifications_dedupe_key_active
    ON notifications (dedupe_key)
    WHERE deleted_at IS NULL;

-- 2) Notification Deliveries
CREATE TABLE IF NOT EXISTS notification_deliveries (
    id BIGINT PRIMARY KEY, -- Snowflake ID
    notification_id BIGINT NOT NULL,
    channel VARCHAR(20) NOT NULL
        CHECK (channel IN ('IN_APP', 'EMAIL', 'SMS', 'KAKAO', 'PUSH')),
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('PENDING', 'SENT', 'DELIVERED', 'RETRYING', 'FAILED', 'DROPPED', 'BOUNCED')),
    provider VARCHAR(50) NOT NULL,
    provider_message_id VARCHAR(100),
    recipient_address VARCHAR(320),
    attempt_count INT NOT NULL DEFAULT 0
        CHECK (attempt_count >= 0),
    last_error_code VARCHAR(50),
    last_error_message VARCHAR(500),
    next_retry_at TIMESTAMP,
    delivered_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_notification_deliveries_notification
        FOREIGN KEY (notification_id) REFERENCES notifications(id)
);

CREATE INDEX IF NOT EXISTS idx_notification_deliveries_retry
    ON notification_deliveries (status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_notification_deliveries_notification_id
    ON notification_deliveries (notification_id);

-- soft delete 호환 unique
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_deliveries_notification_channel_active
    ON notification_deliveries (notification_id, channel)
    WHERE deleted_at IS NULL;

-- 3) Notification Templates
CREATE TABLE IF NOT EXISTS notification_templates (
    id BIGINT PRIMARY KEY, -- Snowflake ID
    type VARCHAR(40) NOT NULL
        CHECK (type IN ('FUNDING_SUCCESS', 'FUNDING_FAIL', 'PAYMENT', 'HOTDEAL', 'STOCK_DEPLETED', 'CHAT_MESSAGE', 'GENERAL')),
    channel VARCHAR(20) NOT NULL
        CHECK (channel IN ('IN_APP', 'EMAIL', 'SMS', 'KAKAO', 'PUSH')),
    locale VARCHAR(10) NOT NULL,
    title_template VARCHAR(200) NOT NULL,
    message_template VARCHAR(2000) NOT NULL,
    version INT NOT NULL CHECK (version >= 1),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notification_templates_lookup
    ON notification_templates (type, channel, locale, enabled);

-- soft delete 호환 unique
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_templates_type_channel_locale_version_active
    ON notification_templates (type, channel, locale, version)
    WHERE deleted_at IS NULL;

-- 4) Notification Settings
CREATE TABLE IF NOT EXISTS notification_settings (
    id BIGINT PRIMARY KEY, -- Snowflake ID
    user_id BIGINT NOT NULL, -- user-service users.id (FK 미연결)
    type VARCHAR(40) NOT NULL
        CHECK (type IN ('FUNDING_SUCCESS', 'FUNDING_FAIL', 'PAYMENT', 'HOTDEAL', 'STOCK_DEPLETED', 'CHAT_MESSAGE', 'GENERAL')),
    channel VARCHAR(20) NOT NULL
        CHECK (channel IN ('IN_APP', 'EMAIL', 'SMS', 'KAKAO', 'PUSH')),
    enabled BOOLEAN NOT NULL,
    muted_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notification_settings_user
    ON notification_settings (user_id);

-- soft delete 호환 unique
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_settings_user_type_channel_active
    ON notification_settings (user_id, type, channel)
    WHERE deleted_at IS NULL;

-- 5) Notification User Preferences
CREATE TABLE IF NOT EXISTS notification_user_preferences (
    id BIGINT PRIMARY KEY, -- Snowflake ID
    user_id BIGINT NOT NULL, -- user-service users.id (FK 미연결)
    global_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    timezone VARCHAR(50) NOT NULL,
    quiet_hours_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    quiet_hours_start TIME,
    quiet_hours_end TIME,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    CONSTRAINT chk_notification_user_preferences_quiet_hours
        CHECK (
            quiet_hours_enabled = FALSE
            OR (quiet_hours_start IS NOT NULL AND quiet_hours_end IS NOT NULL)
        )
);

CREATE INDEX IF NOT EXISTS idx_notification_user_preferences_user_id
    ON notification_user_preferences (user_id);

-- soft delete 호환 unique
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_user_preferences_user_id_active
    ON notification_user_preferences (user_id)
    WHERE deleted_at IS NULL;

-- 6) Processed Events (libs/config/kafka)
CREATE TABLE IF NOT EXISTS processed_events (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('PROCESSING', 'PROCESSED', 'FAILED')),
    processed_at TIMESTAMP,
    error_message TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_event_id
    ON processed_events (event_id);

-- 7) Dead Letter Messages (libs/config/kafka)
CREATE TABLE IF NOT EXISTS dead_letter_messages (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    topic VARCHAR(255) NOT NULL,
    partition_num INT,
    offset_num BIGINT,
    key_value VARCHAR(255),
    payload TEXT,
    error_message TEXT,
    event_id VARCHAR(255),
    event_type VARCHAR(255),
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('UNRESOLVED', 'RETRYING', 'RESOLVED')),
    retry_count INT NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    created_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dlm_status
    ON dead_letter_messages (status);
CREATE INDEX IF NOT EXISTS idx_dlm_topic_created
    ON dead_letter_messages (topic, created_at);
CREATE INDEX IF NOT EXISTS idx_dlm_event_id
    ON dead_letter_messages (event_id);

-- 8) Outbox (libs/event/outbox)
CREATE TABLE IF NOT EXISTS outbox_messages (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('PENDING', 'SENDING', 'PUBLISHED', 'FAILED')),
    published_at TIMESTAMP,
    retry_count INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    error_message VARCHAR(500),
    correlation_id VARCHAR(255),
    causation_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_outbox_event_id
    ON outbox_messages (event_id);
CREATE INDEX IF NOT EXISTS idx_outbox_status_created
    ON outbox_messages (status, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_status_updated
    ON outbox_messages (status, updated_at);
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate
    ON outbox_messages (aggregate_type, aggregate_id);
