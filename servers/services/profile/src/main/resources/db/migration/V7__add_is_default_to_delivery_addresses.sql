ALTER TABLE profile_delivery_addresses
    ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT FALSE;

-- 프로필별로 sort_order가 가장 낮은 주소를 기본 주소로 초기화
UPDATE profile_delivery_addresses pda
SET is_default = TRUE
WHERE id IN (
    SELECT DISTINCT ON (profile_id) id
    FROM profile_delivery_addresses
    ORDER BY profile_id, sort_order ASC
);
