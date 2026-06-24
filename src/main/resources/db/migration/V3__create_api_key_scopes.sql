-- =============================================
-- V3: api_key_scopes 테이블 생성
-- =============================================
CREATE TABLE api_key_scopes (
    id                      BIGSERIAL PRIMARY KEY,
    api_key_id              BIGINT       NOT NULL REFERENCES api_keys(id),
    path_pattern            VARCHAR(500) NOT NULL,
    allowed_methods         JSONB        NOT NULL,  -- ex) ["GET", "POST"]
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted              BOOLEAN NOT NULL DEFAULT FALSE,
    created_by              VARCHAR(100),
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by              VARCHAR(100),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_api_key_scopes_api_key_id    ON api_key_scopes(api_key_id)    WHERE is_deleted = FALSE;
CREATE INDEX idx_api_key_scopes_path_pattern  ON api_key_scopes(path_pattern)  WHERE is_deleted = FALSE;
