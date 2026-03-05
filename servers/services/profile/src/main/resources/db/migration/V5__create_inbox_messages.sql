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
