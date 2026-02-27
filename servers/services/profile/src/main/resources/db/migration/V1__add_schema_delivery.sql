CREATE TABLE IF NOT EXISTS profiles (
                                        id          BIGINT PRIMARY KEY,
                                        user_id     BIGINT NOT NULL UNIQUE,
                                        email       VARCHAR(100) NOT NULL,
    nickname    VARCHAR(10),
    phone_number       VARCHAR(15),
    created_at  TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at  TIMESTAMP(6) WITHOUT TIME ZONE
    );

CREATE INDEX IF NOT EXISTS idx_profiles_nickname ON profiles (nickname);
CREATE INDEX IF NOT EXISTS idx_profiles_deleted_at ON profiles (deleted_at);

CREATE TABLE IF NOT EXISTS profile_images (
                                              user_id     BIGINT PRIMARY KEY,
                                              media_id    BIGINT default null,
                                              created_at  TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at  TIMESTAMP(6) WITHOUT TIME ZONE,

    CONSTRAINT fk_profile_images_profile
    FOREIGN KEY (user_id)
    REFERENCES profiles(user_id)
                                                                       ON DELETE CASCADE
    );

CREATE INDEX IF NOT EXISTS idx_profile_images_deleted_at ON profile_images (deleted_at);
CREATE INDEX IF NOT EXISTS idx_profile_media_id ON profile_images (media_id);

-- profile 1 : delivery 3(최대 3개)
CREATE TABLE IF NOT EXISTS profile_delivery_addresses (
      id              BIGINT PRIMARY KEY,
      profile_id      BIGINT NOT NULL,


      delivery_name varchar(20) NOT NULL, -- 집 , 회사, 별칭 지정
    zipcode         VARCHAR(10) NOT NULL,
    sido            VARCHAR(30) NOT NULL,
    sigungu         VARCHAR(30) NOT NULL,
    road_name       VARCHAR(100) NOT NULL,
    building_number VARCHAR(20) NOT NULL,
    building_name   VARCHAR(100),
    detail_address  VARCHAR(255),
    sort_order  INT DEFAULT 0,

    created_at      TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP(6) WITHOUT TIME ZONE,

    CONSTRAINT fk_delivery_profile FOREIGN KEY (profile_id) REFERENCES profiles(id) ON DELETE CASCADE
    );

CREATE INDEX IF NOT EXISTS idx_delivery_profile_id ON profile_delivery_addresses(profile_id);
CREATE INDEX IF NOT EXISTS idx_delivery_deleted_at ON profile_delivery_addresses(deleted_at);


-- ✅ [수정] INSERT + UPDATE(소프트딜리트 복구) 모두 커버하도록 트리거 함수 수정
CREATE OR REPLACE FUNCTION check_delivery_limit()
    RETURNS trigger AS $$
BEGIN
        -- 부모 row에 락 (동시성 제어)
        PERFORM 1
        FROM profiles
        WHERE id = NEW.profile_id
        FOR UPDATE;

-- ✅ [수정] UPDATE 시 본인 row 제외하고 카운트 (소프트딜리트 복구 케이스 대응)
IF TG_OP = 'UPDATE' THEN
            IF (
                SELECT COUNT(*)
                FROM profile_delivery_addresses
                WHERE profile_id = NEW.profile_id
                  AND deleted_at IS NULL
                  AND id != NEW.id  -- 자기 자신 제외
            ) >= 3 THEN
                RAISE EXCEPTION '배송지는 최대 3개까지 등록 가능합니다.';
END IF;
ELSE
            -- INSERT
            IF (
                SELECT COUNT(*)
                FROM profile_delivery_addresses
                WHERE profile_id = NEW.profile_id
                  AND deleted_at IS NULL
            ) >= 3 THEN
                RAISE EXCEPTION '배송지는 최대 3개까지 등록 가능합니다.';
END IF;
END IF;

RETURN NEW;
END;
    $$ LANGUAGE plpgsql;


-- ✅ [추가] 트리거 바인딩 (INSERT + UPDATE 모두 적용)
CREATE TRIGGER trg_check_delivery_limit
    BEFORE INSERT OR UPDATE ON profile_delivery_addresses
                         FOR EACH ROW EXECUTE FUNCTION check_delivery_limit();


-- =====================
-- 1. profiles
-- =====================
INSERT INTO profiles (id, user_id, email, nickname, phone_number, created_at, updated_at) VALUES
                                                                                              (1, 101, 'alice@example.com',   'Alice',   '010-1111-1111', NOW(), NOW()),
                                                                                              (2, 102, 'bob@example.com',     'Bob',     '010-2222-2222', NOW(), NOW()),
                                                                                              (3, 103, 'charlie@ex.com',      'Charlie', '010-3333-3333', NOW(), NOW()),
                                                                                              (4, 104, 'david@example.com',   'David',   '010-4444-4444', NOW(), NOW()),
                                                                                              (5, 105, 'eve@example.com',     'Eve',     NULL,            NOW(), NOW()); -- phone 없는 케이스


-- =====================
-- 2. profile_images (1:1)
-- user_id 4명만 등록, eve(105)는 미등록
-- =====================
INSERT INTO profile_images (user_id, media_id, created_at, updated_at) VALUES
                                                                           (101, 1001, NOW(), NOW()),
                                                                           (102, 1002, NOW(), NOW()),
                                                                           (103, 1003, NOW(), NOW()),
                                                                           (104, 1004, NOW(), NOW());


-- =====================
-- 3. profile_delivery_addresses (1:최대3)
-- profile_id는 profiles.id 참조
-- =====================

-- Alice(profile_id=1): 배송지 3개 (최대치)
INSERT INTO profile_delivery_addresses (id, profile_id, delivery_name, zipcode, sido, sigungu, road_name, building_number, building_name, detail_address, sort_order, created_at, updated_at)
VALUES
      (1001, 1, '집',    '06000', '서울특별시', '강남구', '테헤란로',   '427', '강남빌딩', '3층 301호',  0, NOW(), NOW()),
      (1002, 1, '회사',  '06100', '서울특별시', '강남구', '역삼로',     '100', NULL,       '2층',        1, NOW(), NOW()),
      (1003, 1, '친정',  '06200', '서울특별시', '서초구', '서초대로',   '55',  '서초타워', '10층',       2, NOW(), NOW());

-- Bob(profile_id=2): 배송지 1개
INSERT INTO profile_delivery_addresses (id, profile_id, delivery_name, zipcode, sido, sigungu, road_name, building_number, building_name, detail_address, sort_order, created_at, updated_at)
VALUES
    (1004, 2, '집', '04000', '서울특별시', '마포구', '월드컵북로', '12', NULL, '빌라 201호', 0, NOW(), NOW());

-- Charlie(profile_id=3): 배송지 2개
INSERT INTO profile_delivery_addresses (id, profile_id, delivery_name, zipcode, sido, sigungu, road_name, building_number, building_name, detail_address, sort_order, created_at, updated_at)
VALUES
    (1005, 3, '집',   '48000', '부산광역시', '해운대구', '해운대해변로', '30', '해운대아파트', '101동 501호', 0, NOW(), NOW()),
    (1006, 3, '회사', '48100', '부산광역시', '해운대구', '좌동순환로',   '50', NULL,           '302호',       1, NOW(), NOW());

-- David(profile_id=4): 배송지 없음
-- Eve(profile_id=5):   배송지 없음, 이미지도 없음

