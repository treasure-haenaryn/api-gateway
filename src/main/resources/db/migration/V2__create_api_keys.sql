-- =============================================
-- V2: api_keys 테이블 생성
-- =============================================
CREATE TABLE api_keys (
    id                      BIGSERIAL PRIMARY KEY,
    key_prefix              VARCHAR(20)  NOT NULL,
    key_hash                VARCHAR(256) NOT NULL UNIQUE,
    name                    VARCHAR(200) NOT NULL,
    description             TEXT,
    owner                   VARCHAR(200),
    key_type                VARCHAR(20)  NOT NULL DEFAULT 'LIVE',  -- LIVE / TEST / INTERNAL
    ip_whitelist            JSONB,
    rate_limit_per_sec      INTEGER,
    rate_limit_per_day      INTEGER,
    rate_limit_per_month    INTEGER,
    expires_at              TIMESTAMP,
    last_used_at            TIMESTAMP,
    revoked_at              TIMESTAMP,
    revoked_by              VARCHAR(100),
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted              BOOLEAN NOT NULL DEFAULT FALSE,
    created_by              VARCHAR(100),
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by              VARCHAR(100),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_api_keys_key_hash   ON api_keys(key_hash)   WHERE is_deleted = FALSE;
CREATE INDEX idx_api_keys_key_prefix ON api_keys(key_prefix) WHERE is_deleted = FALSE;
CREATE INDEX idx_api_keys_is_active  ON api_keys(is_active)  WHERE is_deleted = FALSE;
