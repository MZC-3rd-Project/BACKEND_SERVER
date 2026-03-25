ALTER TABLE profile_delivery_addresses
    ADD COLUMN recipient_name  VARCHAR(50),
    ADD COLUMN recipient_phone VARCHAR(20);
