

CREATE TABLE IF NOT EXISTS profiles (
                                        id BIGINT PRIMARY KEY,
                                        user_id BIGINT NOT NULL,
                                        email               VARCHAR(255) NOT NULL,                                  -- Keycloak에서 복제 (내부 조회용)
    nickname            VARCHAR(100),
    phone               VARCHAR(15),
    delivery            VARCHAR(100),
    created_at      TIMESTAMP(6) WITHOUT TIME ZONE
    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP(6) WITHOUT TIME ZONE
    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP(6) WITHOUT TIME ZONE

    );
CREATE INDEX idx_profiles_emails ON profiles (email);
CREATE INDEX idx_profiles_deleted_at ON profiles (deleted_at);

CREATE TABLE profile_images (
                                profile_id      BIGINT NOT NULL ,                 -- profiles.id (pk + fk)

                                media_id       VARCHAR(500) NOT NULL,

                                sort_order      INT DEFAULT 0,

                                created_at      TIMESTAMP(6) WITHOUT TIME ZONE
                        NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                updated_at      TIMESTAMP(6) WITHOUT TIME ZONE
      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                deleted_at      TIMESTAMP(6) WITHOUT TIME ZONE,

                                CONSTRAINT fk_profile_images_profile
                                    FOREIGN KEY (profile_id)
                                        REFERENCES profiles(id)
                                        ON DELETE CASCADE
);

CREATE INDEX idx_profile_images_profile_id
    ON profile_images (profile_id);
CREATE INDEX idx_profile_images_media_id
    ON profile_images(media_id);
CREATE INDEX idx_profile_images_deleted_at
    ON profile_images (deleted_at);


INSERT INTO profiles (id, user_id, email, nickname, phone, delivery, created_at, updated_at) VALUES
                                                                                                 (1, 101, 'alice@example.com',   'Alice',   '010-1111-1111', '서울시 강남구', NOW(), NOW()),
                                                                                                 (2, 102, 'bob@example.com',     'Bob',     '010-2222-2222', '서울시 마포구', NOW(), NOW()),
                                                                                                 (3, 103, 'charlie@example.com', 'Charlie', '010-3333-3333', '부산시 해운대구', NOW(), NOW()),
                                                                                                 (4, 104, 'david@example.com',   'David',   '010-4444-4444', '대구시 중구', NOW(), NOW()),
                                                                                                 (5, 105, 'eve@example.com',     'Eve',     '010-5555-5555', '인천시 연수구', NOW(), NOW());

INSERT INTO profile_images (profile_id, media_id, sort_order, created_at, updated_at) VALUES
                                                                                          (1, 'media-alice-001',   0, NOW(), NOW()),
                                                                                          (1, 'media-alice-002',   1, NOW(), NOW()),
                                                                                          (2, 'media-bob-001',     0, NOW(), NOW()),
                                                                                          (3, 'media-charlie-001', 0, NOW(), NOW()),
                                                                                          (4, 'media-david-001',   0, NOW(), NOW());
