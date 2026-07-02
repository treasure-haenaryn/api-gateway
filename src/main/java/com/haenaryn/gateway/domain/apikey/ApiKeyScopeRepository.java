package com.haenaryn.gateway.domain.apikey;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ApiKeyScopeRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    // allowed_methods가 JSONB타입이므로 직접 RowMapper 구현
    private ApiKeyScopeRecord mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        List<String> allowedMethods = Collections.emptyList();
        try {
            Object obj = rs.getObject("allowed_methods");
            if (obj instanceof PGobject pgObj) {
                allowedMethods = objectMapper.readValue(
                        pgObj.getValue(), new TypeReference<List<String>>() {});
            }
        } catch (Exception e) {
            log.error("[ApiKeyScopeRepository] allowed_methods 파싱 오류", e);
        }

        return ApiKeyScopeRecord.builder()
                .id(rs.getLong("id"))
                .apiKeyId(rs.getLong("api_key_id"))
                .pathPattern(rs.getString("path_pattern"))
                .allowedMethods(allowedMethods)
                .isActive(rs.getBoolean("is_active"))
                .build();
    }

    public List<ApiKeyScopeRecord> findAllActive() {
        return jdbcClient
                .sql("""
                        SELECT *
                        FROM api_key_scopes
                        WHERE is_active = true
                          AND is_deleted = false
                        """)
                .query(this::mapRow)
                .list();
    }

    public List<ApiKeyScopeRecord> findByApiKeyId(Long apiKeyId) {
        return jdbcClient
                .sql("""
                        SELECT *
                        FROM api_key_scopes
                        WHERE api_key_id = :apiKeyId
                          AND is_deleted = false
                        """)
                .param("apiKeyId", apiKeyId)
                .query(this::mapRow)
                .list();
    }
}
