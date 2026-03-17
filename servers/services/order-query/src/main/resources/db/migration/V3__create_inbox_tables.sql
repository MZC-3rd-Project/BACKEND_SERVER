CREATE TABLE IF NOT EXISTS inbox_messages (
    id               BIGSERIAL    PRIMARY KEY,
    consumer_name    VARCHAR(100) NOT NULL,
    event_id         VARCHAR(100) NOT NULL,
    event_type       VARCHAR(100),
    payload          TEXT,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count      INT          NOT NULL DEFAULT 0,
    next_retry_at    TIMESTAMP,
    lease_until      TIMESTAMP,
    processed_at     TIMESTAMP,
    error_message    TEXT,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_inbox_consumer_event UNIQUE (consumer_name, event_id)
);

CREATE INDEX IF NOT EXISTS idx_inbox_status_retry ON inbox_messages(status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_inbox_lease        ON inbox_messages(lease_until) WHERE status = 'PROCESSING';

CREATE TABLE IF NOT EXISTS dead_letter_messages (
    id            BIGSERIAL    PRIMARY KEY,
    consumer_name VARCHAR(100) NOT NULL,
    event_id      VARCHAR(100),
    event_type    VARCHAR(100),
    payload       TEXT,
    error_message TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);
