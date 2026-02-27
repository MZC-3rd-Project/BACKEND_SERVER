-- ================================================
-- Auth Service - V2 이메일 인증 테이블
-- ================================================

CREATE TABLE IF NOT EXISTS email_verifications (
    id          BIGINT       PRIMARY KEY,
    email       VARCHAR(255) NOT NULL,
    code        VARCHAR(10)  NOT NULL,
    verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    expires_at  TIMESTAMP    NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_email_verifications_email ON email_verifications (email);

