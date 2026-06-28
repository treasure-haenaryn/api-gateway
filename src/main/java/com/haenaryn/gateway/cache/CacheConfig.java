package com.haenaryn.gateway.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    public static final String ROUTE_CACHE     = "routeCache";
    public static final String API_KEY_CACHE   = "apiKeyCache";
    public static final String API_SCOPE_CACHE = "apiScopeCache";

    @Value("${gateway.cache.route-max-size:1000}")
    private int routeMaxSize;

    @Value("${gateway.cache.apikey-max-size:10000}")
    private int apikeyMaxSize;

    @Value("${gateway.cache.scope-max-size:10000}")
    private int scopeMaxSize;

    @Bean
    public CacheManager cacheManager(MeterRegistry meterRegistry) {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        manager.registerCustomCache(ROUTE_CACHE,
                buildCache(meterRegistry, ROUTE_CACHE, routeMaxSize));

        manager.registerCustomCache(API_KEY_CACHE,
                buildCache(meterRegistry, API_KEY_CACHE, apikeyMaxSize));

        manager.registerCustomCache(API_SCOPE_CACHE,
                buildCache(meterRegistry, API_SCOPE_CACHE, scopeMaxSize));

        return manager;
    }

    // Caffeine 네이티브 Cache 생성
    // → Micrometer 등록, removalListener 설정은 네이티브 API에서만 가능
    // → Spring Cache 추상화는 이 설정들을 제공하지 않음
    private Cache<Object, Object> buildCache(
            MeterRegistry meterRegistry, String cacheName, int maximumSize) {

        Cache<Object, Object> cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .recordStats()
                .removalListener((Object key, Object value, RemovalCause cause) ->
                        log.debug("[{}] 항목 제거 — key: {}, 이유: {}", cacheName, key, cause))
                .build();

        // Micrometer에 직접 등록 (Spring Boot가 registerCustomCache 방식은 자동 등록 안 함)
        CaffeineCacheMetrics.monitor(meterRegistry, cache, cacheName, Collections.emptyList());

        return cache;
    }
}
