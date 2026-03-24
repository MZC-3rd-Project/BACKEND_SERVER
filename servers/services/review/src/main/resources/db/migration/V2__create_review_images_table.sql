CREATE TABLE review_images (
    id BIGINT PRIMARY KEY,
    review_id BIGINT NOT NULL REFERENCES reviews(id),
    media_id BIGINT NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX idx_review_images_review
    ON review_images (review_id, sort_order);
