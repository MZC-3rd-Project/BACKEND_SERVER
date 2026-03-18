-- orders 테이블에 누락된 컬럼 추가
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS recipient_name  VARCHAR(100),
    ADD COLUMN IF NOT EXISTS recipient_phone VARCHAR(20),
    ADD COLUMN IF NOT EXISTS delivery_memo   VARCHAR(500),
    ADD COLUMN IF NOT EXISTS expires_at      TIMESTAMP;

-- order_items 테이블에 누락된 컬럼 추가
ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS store_id        BIGINT,
    ADD COLUMN IF NOT EXISTS store_name_snap VARCHAR(100),
    ADD COLUMN IF NOT EXISTS channel_type    VARCHAR(20),
    ADD COLUMN IF NOT EXISTS channel_ref_id  BIGINT,
    ADD COLUMN IF NOT EXISTS unit_price      BIGINT,
    ADD COLUMN IF NOT EXISTS line_amount     BIGINT,
    ADD COLUMN IF NOT EXISTS thumbnail_url   VARCHAR(500);

-- FK 제약 제거 (이벤트 기반 프로젝션에서는 FK 순서 보장 불가)
ALTER TABLE order_items DROP CONSTRAINT IF EXISTS fk_order_items_item;
ALTER TABLE order_items DROP CONSTRAINT IF EXISTS fk_order_items_order;
ALTER TABLE orders      DROP CONSTRAINT IF EXISTS fk_orders_user;

-- 인덱스 추가
CREATE INDEX IF NOT EXISTS idx_order_items_store ON order_items(store_id);
