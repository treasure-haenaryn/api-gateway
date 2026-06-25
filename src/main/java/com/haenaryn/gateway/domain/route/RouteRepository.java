package com.haenaryn.gateway.domain.route;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RouteRepository {

    private final JdbcClient jdbcClient;

    public List<RouteRecord> findAllActive() {
        return jdbcClient
                .sql("""
                        SELECT *
                        FROM routes
                        WHERE is_active = true
                          AND is_deleted = false
                        ORDER BY priority ASC
                        """)
                .query(RouteRecord.class)
                .list();
    }

    public Optional<RouteRecord> findByRouteId(String routeId) {
        return jdbcClient
                .sql("""
                        SELECT *
                        FROM routes
                        WHERE route_id = :routeId
                          AND is_deleted = false
                        """)
                .param("routeId", routeId)
                .query(RouteRecord.class)
                .optional();
    }
}
