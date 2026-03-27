ALTER TABLE dead_letter_messages
    ADD COLUMN IF NOT EXISTS consumer_attempt_count INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS last_retried_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS consumer_failure_alerted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS retry_failure_alerted_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_dlm_status_next_retry
    ON dead_letter_messages (status, next_retry_at);
