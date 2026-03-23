CREATE TABLE IF NOT EXISTS payments (
    id             BIGINT PRIMARY KEY,
    order_id       BIGINT NOT NULL,
    user_id        BIGINT NOT NULL,
    status         VARCHAR(30) NOT NULL DEFAULT 'READY',
    amount         BIGINT NOT NULL,
    payment_key    VARCHAR(200),
    toss_order_id  VARCHAR(200),
    method         VARCHAR(50),
    paid_at        TIMESTAMP,
    failed_at      TIMESTAMP,
    fail_reason    VARCHAR(500),
    cancelled_at   TIMESTAMP,
    cancel_reason  VARCHAR(500),
    expires_at     TIMESTAMP,
    toss_response  TEXT,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at     TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_payments_order_id
    ON payments (order_id);

CREATE INDEX IF NOT EXISTS idx_payments_user_id
    ON payments (user_id);

CREATE INDEX IF NOT EXISTS idx_payments_status
    ON payments (status);

CREATE INDEX IF NOT EXISTS idx_payments_payment_key
    ON payments (payment_key);

CREATE INDEX IF NOT EXISTS idx_payments_status_expires_at
    ON payments (status, expires_at);
