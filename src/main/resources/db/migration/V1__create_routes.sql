-- =============================================
-- V1: routes 테이블 생성
-- =============================================
CREATE TABLE routes (
    id                      BIGSERIAL PRIMARY KEY,
    route_id                VARCHAR(100) NOT NULL UNIQUE,
    downstream_uri          VARCHAR(500) NOT NULL,
    priority                INTEGER NOT NULL DEFAULT 0,
    predicates              JSONB,
    filters                 JSONB,
    connect_timeout_ms      INTEGER DEFAULT 3000,
    response_timeout_ms     INTEGER DEFAULT 10000,
    retry_config            JSONB,
    circuit_breaker_config  JSONB,
    fallback_message        TEXT,
    metadata                JSONB,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted              BOOLEAN NOT NULL DEFAULT FALSE,
    created_by              VARCHAR(100),
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by              VARCHAR(100),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_routes_priority   ON routes(priority)  WHERE is_deleted = FALSE;
CREATE INDEX idx_routes_is_active  ON routes(is_active) WHERE is_deleted = FALSE;
CREATE INDEX idx_routes_route_id   ON routes(route_id)  WHERE is_deleted = FALSE;
