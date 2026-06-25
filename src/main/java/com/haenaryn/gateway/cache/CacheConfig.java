package com.haenaryn.gateway.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

@Slf4j
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String ROUTE_CACHE = "routeCache";

    // Spring Cache 추상화 기반 CaffeineCacheManager
    @Bean
    public CacheManager cacheManager(MeterRegistry meterRegistry) {
        com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache =
                Caffeine.newBuilder()
                        .maximumSize(1_000)
                        .recordStats()
                        .removalListener((Object key, Object value, RemovalCause cause) ->
                                log.debug("[RouteCache] 항목 제거 — key: {}, 이유: {}", key, cause))
                        .build();

        // Spring Boot가 자동으로 등록하지 않으므로 Micrometer에 직접 등록
        CaffeineCacheMetrics.monitor(meterRegistry, nativeCache, ROUTE_CACHE, Collections.emptyList());

        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache(ROUTE_CACHE, nativeCache);

        return manager;
    }
}
