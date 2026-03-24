CREATE TABLE reviews (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    rating INTEGER NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE UNIQUE INDEX uk_reviews_order_item_user
    ON reviews (order_id, item_id, user_id);

CREATE INDEX idx_reviews_item_created
    ON reviews (item_id, created_at DESC);

CREATE INDEX idx_reviews_user_created
    ON reviews (user_id, created_at DESC);
