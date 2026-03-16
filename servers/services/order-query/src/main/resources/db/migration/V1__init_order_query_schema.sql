-- 1. users
CREATE TABLE users (
    id         BIGINT      PRIMARY KEY,
    nickname   VARCHAR(50) NOT NULL,
    role       VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP,
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'SELLER', 'ADMIN'))
);

-- 2. items
CREATE TABLE items (
    id         BIGINT       PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    title      VARCHAR(200) NOT NULL,
    price      BIGINT       NOT NULL CHECK (price >= 0),
    item_type  VARCHAR(20)  NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP,
    CONSTRAINT fk_items_user    FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_items_type   CHECK (item_type IN ('PERFORMANCE', 'GOODS', 'PRODUCT')),
    CONSTRAINT chk_items_status CHECK (status IN ('DRAFT', 'FUNDING', 'FUNDED', 'FUND_FAILED', 'ON_SALE', 'HOT_DEAL', 'HIDDEN', 'SOLD_OUT', 'CLOSED'))
);

-- 3. item_thumbnails
CREATE TABLE item_thumbnails (
    id            BIGINT       PRIMARY KEY,
    item_id       BIGINT       NOT NULL,
    thumbnail_url VARCHAR(500) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMP,
    CONSTRAINT fk_thumbnails_item FOREIGN KEY (item_id) REFERENCES items(id)
);

-- 4. orders
CREATE TABLE orders (
    id           BIGINT      PRIMARY KEY,
    user_id      BIGINT      NOT NULL,
    source_type  VARCHAR(30) NOT NULL,
    status       VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    total_amount BIGINT      NOT NULL CHECK (total_amount >= 0),
    created_at   TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP   NOT NULL DEFAULT NOW(),
    deleted_at   TIMESTAMP,
    CONSTRAINT fk_orders_user    FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_orders_source CHECK (source_type IN ('FUNDING', 'GENERAL', 'HOT_DEAL')),
    CONSTRAINT chk_orders_status CHECK (status IN (
        'PENDING',
        'PAID',
        'PREPARING',
        'SHIPPING',
        'DELIVERED',
        'CANCELLED',
        'REFUNDED'
    ))
);

-- 5. order_items
CREATE TABLE order_items (
    id             BIGINT       PRIMARY KEY,
    order_id       BIGINT       NOT NULL,
    item_id        BIGINT       NOT NULL,
    item_type_snap VARCHAR(20)  NOT NULL,
    title_snap     VARCHAR(200) NOT NULL,
    price_snap     BIGINT       NOT NULL,
    quantity       INT          NOT NULL DEFAULT 1 CHECK (quantity > 0),
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_order_items_item  FOREIGN KEY (item_id)  REFERENCES items(id),
    CONSTRAINT chk_order_items_type CHECK (item_type_snap IN ('PERFORMANCE', 'GOODS', 'PRODUCT'))
);

-- 6. shipments (GOODS / PRODUCT 전용)
CREATE TABLE shipments (
    id               BIGINT       PRIMARY KEY,
    order_id         BIGINT       NOT NULL UNIQUE,
    recipient_name   VARCHAR(50)  NOT NULL,
    shipping_address VARCHAR(500) NOT NULL,
    delivery_type    VARCHAR(20)  NOT NULL,
    status           VARCHAR(30)  NOT NULL DEFAULT 'PREPARING',
    shipped_at       TIMESTAMP,
    delivered_at     TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted_at       TIMESTAMP,
    CONSTRAINT fk_shipments_order   FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT,
    CONSTRAINT chk_shipments_dtype  CHECK (delivery_type IN ('STANDARD', 'EXPRESS', 'PICKUP')),
    CONSTRAINT chk_shipments_status CHECK (status IN ('PREPARING', 'SHIPPED', 'IN_TRANSIT', 'DELIVERED', 'FAILED'))
);

-- 7. tickets (PERFORMANCE 전용)
CREATE TABLE tickets (
    id            BIGINT       PRIMARY KEY,
    order_item_id BIGINT       NOT NULL UNIQUE,
    event_date    TIMESTAMP    NOT NULL,
    venue         VARCHAR(200) NOT NULL,
    seat_info     VARCHAR(100),
    status        VARCHAR(20)  NOT NULL DEFAULT 'RESERVED',
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_tickets_order_item FOREIGN KEY (order_item_id) REFERENCES order_items(id),
    CONSTRAINT chk_tickets_status    CHECK (status IN ('RESERVED', 'USED', 'CANCELLED', 'EXPIRED'))
);

-- Indexes
CREATE INDEX idx_items_user_id        ON items(user_id);
CREATE INDEX idx_items_status         ON items(status);
CREATE INDEX idx_item_thumbnails_item ON item_thumbnails(item_id);
CREATE INDEX idx_orders_user_id       ON orders(user_id);
CREATE INDEX idx_orders_status        ON orders(status);
CREATE INDEX idx_order_items_order    ON order_items(order_id);
CREATE INDEX idx_order_items_item     ON order_items(item_id);

-- Column comments
COMMENT ON COLUMN order_items.price_snap  IS '주문 시점 가격 스냅샷 - 이후 가격 변경에 영향받지 않음';
COMMENT ON COLUMN order_items.title_snap  IS '주문 시점 상품명 스냅샷';
COMMENT ON COLUMN shipments.shipped_at    IS '발송 시각';
COMMENT ON COLUMN shipments.delivered_at  IS '배송 완료 시각';
COMMENT ON COLUMN tickets.seat_info       IS '좌석 정보 (예: A구역 12열 5번)';
