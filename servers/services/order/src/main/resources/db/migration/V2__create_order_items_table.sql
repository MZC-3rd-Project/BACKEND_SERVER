CREATE TABLE order_items (
    id              BIGINT PRIMARY KEY,
    order_id        BIGINT NOT NULL REFERENCES orders(id),
    channel_type    VARCHAR(20) NOT NULL,
    channel_ref_id  BIGINT,
    item_id         BIGINT NOT NULL,
    store_id        BIGINT NOT NULL,
    quantity        INTEGER NOT NULL,
    unit_price      BIGINT NOT NULL,
    line_amount     BIGINT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_item_id ON order_items(item_id);
