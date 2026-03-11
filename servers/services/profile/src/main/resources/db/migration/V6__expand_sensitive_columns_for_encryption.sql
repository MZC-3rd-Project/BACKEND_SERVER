DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'profiles'
          AND column_name = 'email'
    ) THEN
        ALTER TABLE profiles
            ALTER COLUMN email TYPE VARCHAR(512);
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'profiles'
          AND column_name = 'phone_number'
    ) THEN
        ALTER TABLE profiles
            ALTER COLUMN phone_number TYPE VARCHAR(256);
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'profiles'
          AND column_name = 'phone'
    ) THEN
        ALTER TABLE profiles
            ALTER COLUMN phone TYPE VARCHAR(256);
    END IF;
END $$;
