CREATE OR REPLACE FUNCTION check_delivery_limit()
    RETURNS trigger AS $$
BEGIN
    PERFORM 1 FROM profiles WHERE id = NEW.profile_id FOR UPDATE;

    IF (
        SELECT COUNT(*) FROM profile_delivery_addresses
        WHERE profile_id = NEW.profile_id AND deleted_at IS NULL
    ) >= 3 THEN
        RAISE EXCEPTION '배송지는 최대 3개까지 등록 가능합니다.';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_check_delivery_limit ON profile_delivery_addresses;

CREATE TRIGGER trg_check_delivery_limit
    BEFORE INSERT ON profile_delivery_addresses
    FOR EACH ROW EXECUTE FUNCTION check_delivery_limit();
