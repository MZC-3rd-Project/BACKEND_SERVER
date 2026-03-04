-- 1. store (가게 핵심 식별 정보)
CREATE TABLE IF NOT EXISTS stores (
                        id BIGINT PRIMARY KEY,
                        user_id BIGINT NOT NULL UNIQUE,
                        store_name VARCHAR(200) NOT NULL,
                        status VARCHAR(20) NOT NULL DEFAULT 'INACTIVE',
                        created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                        updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
                        deleted_at TIMESTAMP,

                        CONSTRAINT chk_stores_status
                            CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'))
);

-- 2. store_addresses
CREATE TABLE IF NOT EXISTS store_addresses (
                                 id BIGINT PRIMARY KEY,
                                 store_id BIGINT NOT NULL,
                                 address_type VARCHAR(30) NOT NULL DEFAULT 'MAIN',
                                 address VARCHAR(400) NOT NULL,
                                 is_default BOOLEAN NOT NULL DEFAULT FALSE,
                                 created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                 updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                 deleted_at TIMESTAMP,

                                 CONSTRAINT fk_store_addresses_store
                                     FOREIGN KEY (store_id) REFERENCES stores(id),

                                 CONSTRAINT chk_store_address_type
                                     CHECK (address_type IN ('MAIN', 'PICKUP', 'RETURN', 'WAREHOUSE'))
);

-- 3. store_contacts (연락처 - 1:N)
CREATE TABLE IF NOT EXISTS store_contacts (
                                id            BIGINT       PRIMARY KEY,
                                store_id      BIGINT       NOT NULL,
                                contact_type  VARCHAR(30)  NOT NULL,
                                contact_value VARCHAR(100) NOT NULL,
                                is_primary    BOOLEAN      NOT NULL DEFAULT FALSE,
                                created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
                                updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
                                deleted_at    TIMESTAMP,

                                CONSTRAINT fk_contacts_store
                                    FOREIGN KEY (store_id) REFERENCES stores(id),

                                CONSTRAINT chk_contact_type
                                    CHECK (contact_type IN ('PHONE', 'EMAIL', 'KAKAO', 'SNS'))
);

-- 4. store_profiles (소개글 - 1:1)
CREATE TABLE IF NOT EXISTS store_profiles (
                                              id          BIGINT PRIMARY KEY,
                                              store_id    BIGINT NOT NULL UNIQUE,
                                              description TEXT,
                                              created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_profiles_store FOREIGN KEY (store_id) REFERENCES stores(id)
);

-- 5. store_images (이미지 - 1:N)
--    image_type으로 이미지 역할 구분
CREATE TABLE IF NOT EXISTS store_images (
                              id          BIGINT       PRIMARY KEY,
                              store_id    BIGINT       NOT NULL,
                              image_type  VARCHAR(30)  NOT NULL,
                              media_id    VARCHAR(500) NOT NULL,
                              sort_order  INT          NOT NULL DEFAULT 0,
                              created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
                              updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
                              deleted_at  TIMESTAMP,

                              CONSTRAINT fk_images_store
                                  FOREIGN KEY (store_id) REFERENCES stores(id),

                              CONSTRAINT chk_image_type
                                  CHECK (image_type IN ('THUMBNAIL', 'BANNER', 'INTRODUCE'))
);

INSERT INTO stores (id, user_id, store_name, status, created_at, updated_at)
VALUES
    (1001, 501, '베스트전자몰', 'ACTIVE', NOW(), NOW()),
    (1002, 502, '홈리빙스토어', 'ACTIVE', NOW(), NOW()),
    (1003, 503, '패션트렌드샵', 'INACTIVE', NOW(), NOW());

INSERT INTO store_addresses
(id, store_id, address_type, address, is_default, created_at, updated_at)
VALUES
    (2001, 1001, 'MAIN', '서울특별시 금천구 가산디지털로 123', TRUE, NOW(), NOW()),
    (2002, 1001, 'WAREHOUSE', '경기도 김포시 물류단지로 88', FALSE, NOW(), NOW()),
    (2003, 1002, 'MAIN', '경기도 성남시 분당구 판교역로 45', TRUE, NOW(), NOW()),
    (2004, 1003, 'MAIN', '서울특별시 동대문구 장안로 77', TRUE, NOW(), NOW()),
    (2005, 1003, 'WAREHOUSE', '인천광역시 서구 봉수대로 900', FALSE, NOW(), NOW());

INSERT INTO store_contacts
(id, store_id, contact_type, contact_value, is_primary, created_at, updated_at)
VALUES
    (3001, 1001, 'PHONE', '02-1234-5678', TRUE, NOW(), NOW()),
    (3002, 1001, 'EMAIL', 'cs@bestelectronics.co.kr', FALSE, NOW(), NOW()),
    (3003, 1002, 'PHONE', '031-9876-5432', TRUE, NOW(), NOW()),
    (3004, 1003, 'PHONE', '02-555-1234', TRUE, NOW(), NOW()),
    (3005, 1003, 'SNS', 'instagram.com/fashiontrend', FALSE, NOW(), NOW());

INSERT INTO store_profiles
(id, store_id, description, created_at, updated_at)
VALUES
    (4001, 1001, '가전 및 IT 전문 셀러. 정품만 판매합니다.', NOW(), NOW()),
    (4002, 1002, '생활용품과 인테리어 소품을 판매하는 리빙 전문 스토어입니다.', NOW(), NOW()),
    (4003, 1003, '최신 트렌드 의류 및 잡화를 합리적인 가격에 제공합니다.', NOW(), NOW());


INSERT INTO store_images
(id, store_id, image_type, media_id, sort_order, created_at, updated_at)
VALUES
    (5001, 1001, 'THUMBNAIL', 'media/store/bestelectronics_thumb.jpg', 0, NOW(), NOW()),
    (5002, 1001, 'BANNER', 'media/store/bestelectronics_banner.jpg', 0, NOW(), NOW()),
    (5003, 1002, 'THUMBNAIL', 'media/store/homeliving_thumb.jpg', 0, NOW(), NOW()),
    (5004, 1003, 'THUMBNAIL', 'media/store/fashiontrend_thumb.jpg', 0, NOW(), NOW());
