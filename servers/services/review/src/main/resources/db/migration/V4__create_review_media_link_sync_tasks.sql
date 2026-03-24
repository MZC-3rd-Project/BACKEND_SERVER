CREATE TABLE review_media_link_sync_tasks (
    id BIGINT PRIMARY KEY,
    review_id BIGINT NOT NULL,
    gallery_media_ids VARCHAR(5000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP NOT NULL,
    last_error VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE UNIQUE INDEX uk_review_media_link_sync_tasks_review
    ON review_media_link_sync_tasks (review_id);

CREATE INDEX idx_review_media_link_sync_tasks_status_next
    ON review_media_link_sync_tasks (status, next_retry_at);

CREATE INDEX idx_review_media_link_sync_tasks_status_updated
    ON review_media_link_sync_tasks (status, updated_at);
