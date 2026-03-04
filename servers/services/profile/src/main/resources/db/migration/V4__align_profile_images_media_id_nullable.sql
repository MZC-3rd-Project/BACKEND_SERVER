DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'profile_images'
          AND column_name = 'media_id'
          AND is_nullable = 'NO'
    ) THEN
        ALTER TABLE profile_images
            ALTER COLUMN media_id DROP NOT NULL;
    END IF;
END $$;
