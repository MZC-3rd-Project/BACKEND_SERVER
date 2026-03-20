CREATE TABLE IF NOT EXISTS analytics_dim_item_snapshot (
    item_id BIGINT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    item_type VARCHAR(30) NOT NULL,
    item_status VARCHAR(30) NOT NULL,
    price BIGINT,
    stock_quantity BIGINT,
    snapshot_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_analytics_dim_item_snapshot_store
    ON analytics_dim_item_snapshot (store_id);
CREATE INDEX IF NOT EXISTS idx_analytics_dim_item_snapshot_seller
    ON analytics_dim_item_snapshot (seller_id);
CREATE INDEX IF NOT EXISTS idx_analytics_dim_item_snapshot_status
    ON analytics_dim_item_snapshot (item_status);

CREATE TABLE IF NOT EXISTS analytics_raw_sales_event (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(120) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    store_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    order_id BIGINT,
    purchase_id BIGINT,
    user_id BIGINT,
    session_id VARCHAR(120),
    journey_id VARCHAR(120),
    correlation_id VARCHAR(120),
    causation_id VARCHAR(120),
    quantity INTEGER,
    gross_amount BIGINT,
    net_amount BIGINT,
    occurred_at TIMESTAMP NOT NULL,
    ingested_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_analytics_raw_sales_event_event_id
    ON analytics_raw_sales_event (event_id);
CREATE INDEX IF NOT EXISTS idx_analytics_raw_sales_event_store_occurred
    ON analytics_raw_sales_event (store_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_analytics_raw_sales_event_seller_occurred
    ON analytics_raw_sales_event (seller_id, occurred_at);

CREATE TABLE IF NOT EXISTS analytics_raw_search_event (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(120) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    store_id BIGINT,
    seller_id BIGINT,
    item_id BIGINT,
    query_hash VARCHAR(128),
    user_id BIGINT,
    session_id VARCHAR(120),
    journey_id VARCHAR(120),
    correlation_id VARCHAR(120),
    causation_id VARCHAR(120),
    occurred_at TIMESTAMP NOT NULL,
    ingested_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_analytics_raw_search_event_event_id
    ON analytics_raw_search_event (event_id);
CREATE INDEX IF NOT EXISTS idx_analytics_raw_search_event_store_occurred
    ON analytics_raw_search_event (store_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_analytics_raw_search_event_seller_occurred
    ON analytics_raw_search_event (seller_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_analytics_raw_search_event_item_occurred
    ON analytics_raw_search_event (item_id, occurred_at);

CREATE TABLE IF NOT EXISTS analytics_journey_event (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(120) NOT NULL,
    event_sequence INTEGER NOT NULL DEFAULT 0,
    event_type VARCHAR(80) NOT NULL,
    domain_type VARCHAR(40) NOT NULL,
    channel_type VARCHAR(40),
    user_id BIGINT,
    session_id VARCHAR(120),
    journey_id VARCHAR(120),
    correlation_id VARCHAR(120),
    causation_id VARCHAR(120),
    seller_id BIGINT,
    store_id BIGINT,
    item_id BIGINT,
    campaign_id BIGINT,
    hot_deal_id BIGINT,
    order_id BIGINT,
    purchase_id BIGINT,
    participation_id BIGINT,
    quantity INTEGER,
    amount BIGINT,
    query_hash VARCHAR(128),
    properties_json TEXT,
    occurred_at TIMESTAMP NOT NULL,
    ingested_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_analytics_journey_event_event_id
    ON analytics_journey_event (event_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_analytics_journey_event_event_id_sequence
    ON analytics_journey_event (event_id, event_sequence);
CREATE INDEX IF NOT EXISTS idx_analytics_journey_event_store_occurred
    ON analytics_journey_event (store_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_analytics_journey_event_seller_occurred
    ON analytics_journey_event (seller_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_analytics_journey_event_order_event
    ON analytics_journey_event (order_id, event_type);
CREATE INDEX IF NOT EXISTS idx_analytics_journey_event_domain_event
    ON analytics_journey_event (domain_type, event_type, occurred_at);
