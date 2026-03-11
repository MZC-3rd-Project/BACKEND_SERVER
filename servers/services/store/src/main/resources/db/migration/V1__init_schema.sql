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

                                CONSTRAINT fk_profiles_store
                                    FOREIGN KEY (store_id) REFERENCES stores(id)
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


