CREATE TABLE orders (
    id                  BIGINT PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'PAYMENT_PENDING',
    total_amount        BIGINT NOT NULL,
    recipient_name      VARCHAR(100),
    recipient_phone     VARCHAR(20),
    delivery_address_id BIGINT,
    delivery_memo       VARCHAR(500),
    expires_at          TIMESTAMP,
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
