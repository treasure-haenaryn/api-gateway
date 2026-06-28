package com.haenaryn.gateway.domain.apikey;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ApiKeyScopeRepository {

    private final JdbcClient jdbcClient;

    public List<ApiKeyScopeRecord> findAllActive() {
        return jdbcClient
                .sql("""
                        SELECT *
                        FROM api_key_scopes
                        WHERE is_active = true
                          AND is_deleted = false
                        """)
                .query(ApiKeyScopeRecord.class)
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
                .query(ApiKeyScopeRecord.class)
                .list();
    }
}
