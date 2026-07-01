-- =============================================
-- V4: api_keys.key_type 기본값 변경
-- LIVE → EXTERNAL (ApiKeyType enum 변경 반영)
-- =============================================
ALTER TABLE api_keys
    ALTER COLUMN key_type SET DEFAULT 'EXTERNAL';

UPDATE api_keys
SET key_type = 'EXTERNAL'
WHERE key_type = 'LIVE';

COMMENT ON COLUMN api_keys.key_type IS 'EXTERNAL / INTERNAL';
