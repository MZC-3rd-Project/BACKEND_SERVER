
CREATE TABLE IF NOT EXISTS profiles (
    id BIGINT PRIMARY KEY, --- snowflake id,
    user_id BIGINT NOT NULL,
    email               VARCHAR(255) NOT NULL,                                  -- Keycloak에서 복제 (내부 조회용)
    nickname            VARCHAR(100),
    created_at      TIMESTAMP(6) WITHOUT TIME ZONE
    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP(6) WITHOUT TIME ZONE
    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP(6) WITHOUT TIME ZONE
    );
CREATE INDEX idx_profiles_emails ON profiles (email);
CREATE INDEX idx_profiles_deleted_at ON profiles (deleted_at);

CREATE TABLE profile_images (
                                profile_id      BIGINT NOT NULL,
                                media_id       VARCHAR(500) NOT NULL,
                                sort_order      INT DEFAULT 0,
                                created_at      TIMESTAMP(6) WITHOUT TIME ZONE
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


INSERT INTO profiles (id, user_id, email, nickname, created_at, updated_at) VALUES
                                                                                (1, 101, 'alice@example.com',   'Alice',   NOW(), NOW()),
                                                                                (2, 102, 'bob@example.com',     'Bob',     NOW(), NOW()),
                                                                                (3, 103, 'charlie@example.com', 'Charlie', NOW(), NOW()),
                                                                                (4, 104, 'david@example.com',   'David',   NOW(), NOW()),
                                                                                (5, 105, 'eve@example.com',     'Eve',     NOW(), NOW());

INSERT INTO profile_images (profile_id, media_id, sort_order, created_at) VALUES
                                                                              (1, 'media-alice-001',   0, NOW()),
                                                                              (1, 'media-alice-002',   1, NOW()),
                                                                              (2, 'media-bob-001',     0, NOW()),
                                                                              (3, 'media-charlie-001', 0, NOW()),
                                                                              (4, 'media-david-001',   0, NOW());
