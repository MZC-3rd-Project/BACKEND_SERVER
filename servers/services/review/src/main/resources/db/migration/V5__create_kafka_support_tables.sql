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
