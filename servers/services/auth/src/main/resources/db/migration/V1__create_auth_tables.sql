-- ================================================
-- Auth Service - V1 초기 스키마
-- DB: auth_db (PostgreSQL)
-- ================================================

-- 1. users 테이블
CREATE TABLE IF NOT EXISTS users (
    id              BIGINT       PRIMARY KEY,
    keycloak_id     VARCHAR(36)  NOT NULL,
    email           VARCHAR(255) NOT NULL,
    nickname        VARCHAR(50)  NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'USER',
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    auth_provider   VARCHAR(20)  NOT NULL DEFAULT 'KEYCLOAK',
    last_login_at   TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP,

    CONSTRAINT uq_users_keycloak_id UNIQUE (keycloak_id),
    CONSTRAINT uq_users_email       UNIQUE (email)
);

CREATE INDEX IF NOT EXISTS idx_users_status ON users (status);
CREATE INDEX IF NOT EXISTS idx_users_auth_provider ON users (auth_provider);
CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);

-- 2. user_status_histories 테이블
CREATE TABLE IF NOT EXISTS user_status_histories (
    id              BIGINT       PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    previous_status VARCHAR(20)  NOT NULL,
    new_status      VARCHAR(20)  NOT NULL,
    reason          VARCHAR(500),
    changed_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP,

    CONSTRAINT fk_status_history_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS idx_user_status_histories_user_id ON user_status_histories (user_id);
