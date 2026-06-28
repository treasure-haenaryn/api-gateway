package com.haenaryn.gateway.domain.apikey;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ApiKeyRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public List<ApiKeyRecord> findAllActive() {
        return jdbcClient
                .sql("""
                        SELECT *
                        FROM api_keys
                        WHERE is_active = true
                          AND is_deleted = false
                        """)
                .query((rs, rowNum) -> mapRow(rs))
                .list();
    }

    public Optional<ApiKeyRecord> findByKeyHash(String keyHash) {
        return jdbcClient
                .sql("""
                        SELECT *
                        FROM api_keys
                        WHERE key_hash = :keyHash
                          AND is_deleted = false
                        """)
                .param("keyHash", keyHash)
                .query((rs, rowNum) -> mapRow(rs))
                .optional();
    }

    public void updateLastUsedAt(Long id) {
        jdbcClient
                .sql("UPDATE api_keys SET last_used_at = NOW() WHERE id = :id")
                .param("id", id)
                .update();
    }

    private ApiKeyRecord mapRow(ResultSet rs) throws SQLException {
        List<String> ipWhitelist = null;
        String ipWhitelistJson = rs.getString("ip_whitelist");
        if (ipWhitelistJson != null) {
            try {
                ipWhitelist = objectMapper.readValue(ipWhitelistJson, new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("[ApiKeyRepository] ip_whitelist 파싱 오류 — 전체 허용으로 처리");
            }
        }

        return ApiKeyRecord.builder()
                .id(rs.getLong("id"))
                .keyPrefix(rs.getString("key_prefix"))
                .keyHash(rs.getString("key_hash"))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .owner(rs.getString("owner"))
                .keyType(ApiKeyType.valueOf(rs.getString("key_type")))
                .ipWhitelist(ipWhitelist)
                .rateLimitPerSec((Integer) rs.getObject("rate_limit_per_sec"))
                .rateLimitPerDay((Integer) rs.getObject("rate_limit_per_day"))
                .rateLimitPerMonth((Integer) rs.getObject("rate_limit_per_month"))
                .expiresAt(rs.getTimestamp("expires_at") != null
                        ? rs.getTimestamp("expires_at").toLocalDateTime() : null)
                .lastUsedAt(rs.getTimestamp("last_used_at") != null
                        ? rs.getTimestamp("last_used_at").toLocalDateTime() : null)
                .revokedAt(rs.getTimestamp("revoked_at") != null
                        ? rs.getTimestamp("revoked_at").toLocalDateTime() : null)
                .revokedBy(rs.getString("revoked_by"))
                .isActive(rs.getBoolean("is_active"))
                .isDeleted(rs.getBoolean("is_deleted"))
                .createdBy(rs.getString("created_by"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .updatedBy(rs.getString("updated_by"))
                .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                .build();
    }
}
