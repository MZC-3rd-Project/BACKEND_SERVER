ALTER TABLE analytics_dim_item_snapshot
    ADD COLUMN IF NOT EXISTS review_count BIGINT NOT NULL DEFAULT 0;

ALTER TABLE analytics_dim_item_snapshot
    ADD COLUMN IF NOT EXISTS average_rating NUMERIC(4, 2) NOT NULL DEFAULT 0.00;
