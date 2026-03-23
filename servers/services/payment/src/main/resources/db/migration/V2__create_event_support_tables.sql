CREATE TABLE IF NOT EXISTS outbox_messages (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(255) NOT NULL,
    published_at TIMESTAMP,
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(255),
    correlation_id VARCHAR(255),
    causation_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created
    ON outbox_messages (status, created_at);

CREATE INDEX IF NOT EXISTS idx_outbox_status_updated
    ON outbox_messages (status, updated_at);

CREATE INDEX IF NOT EXISTS idx_outbox_aggregate
    ON outbox_messages (aggregate_type, aggregate_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_outbox_event_id
    ON outbox_messages (event_id);

CREATE TABLE IF NOT EXISTS inbox_messages (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT,
    consumer_name VARCHAR(120) NOT NULL,
    event_id VARCHAR(160) NOT NULL,
    event_type VARCHAR(160) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP,
    lease_until TIMESTAMP,
    processed_at TIMESTAMP,
    last_error VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    CONSTRAINT uk_inbox_consumer_event UNIQUE (consumer_name, event_id)
);

CREATE INDEX IF NOT EXISTS idx_inbox_status_nextretry
    ON inbox_messages (status, next_retry_at);

CREATE INDEX IF NOT EXISTS idx_inbox_status_lease
    ON inbox_messages (status, lease_until);

CREATE INDEX IF NOT EXISTS idx_inbox_consumer_status
    ON inbox_messages (consumer_name, status);

CREATE TABLE IF NOT EXISTS processed_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP,
    error_message VARCHAR(255)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_event_id
    ON processed_events (event_id);

CREATE TABLE IF NOT EXISTS dead_letter_messages (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(255) NOT NULL,
    partition_num INTEGER,
    offset_num BIGINT,
    key_value VARCHAR(255),
    payload TEXT,
    error_message TEXT,
    event_id VARCHAR(255),
    event_type VARCHAR(255),
    status VARCHAR(255) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dlm_status
    ON dead_letter_messages (status);

CREATE INDEX IF NOT EXISTS idx_dlm_topic_created
    ON dead_letter_messages (topic, created_at);

CREATE INDEX IF NOT EXISTS idx_dlm_event_id
    ON dead_letter_messages (event_id);
