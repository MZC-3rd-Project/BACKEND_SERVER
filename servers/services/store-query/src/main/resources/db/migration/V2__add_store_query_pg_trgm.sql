CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_store_read_models_store_name_trgm
    ON store_read_models
    USING GIN (store_name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_store_read_models_owner_nickname_trgm
    ON store_read_models
    USING GIN (owner_nickname gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_store_read_models_search_text_trgm
    ON store_read_models
    USING GIN (search_text gin_trgm_ops);
