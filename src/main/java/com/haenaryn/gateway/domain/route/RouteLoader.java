package com.haenaryn.gateway.domain.route;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haenaryn.gateway.cache.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker;
import static org.springframework.cloud.gateway.server.mvc.filter.RetryFilterFunctions.retry;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class RouteLoader implements ApplicationRunner {

    private final RouteRepository routeRepository;
    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) {
        loadRoutes();
    }

    @Scheduled(fixedDelayString = "${gateway.cache.route-reload-interval-ms:30000}")
    public void scheduledReload() {
        log.debug("[RouteLoader] 주기적 라우팅 캐시 리로드 시작");
        loadRoutes();
    }

    public void loadRoutes() {
        List<RouteRecord> routes = routeRepository.findAllActive();

        // 전체 키 목록 조회 → Caffeine 네이티브 사용 (Spring Cache 추상화 미지원)
        com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = getNativeCache();
        Set<String> freshIds = routes.stream()
                .map(RouteRecord::getRouteId)
                .collect(Collectors.toSet());

        nativeCache.asMap().keySet().stream()
                .filter(key -> !freshIds.contains(key))
                .forEach(nativeCache::invalidate);

        // 단건 put() → Spring Cache 추상화 사용
        Cache cache = getCache();
        routes.forEach(r -> cache.put(r.getRouteId(), r));

        log.info("[RouteLoader] 라우팅 캐시 로드 완료 — 총 {}개", routes.size());
    }

    public void refreshRoute(String routeId) {
        Cache cache = getCache();
        routeRepository.findByRouteId(routeId).ifPresentOrElse(
                routeRecord -> {
                    if (routeRecord.isActive()) {
                        cache.put(routeId, routeRecord);
                        log.info("[RouteLoader] 라우팅 캐시 갱신 — routeId: {}", routeId);
                    } else {
                        cache.evict(routeId);
                        log.info("[RouteLoader] 라우팅 캐시 제거 — routeId: {}", routeId);
                    }
                },
                () -> {
                    cache.evict(routeId);
                    log.info("[RouteLoader] 라우팅 캐시 제거 (DB 없음) — routeId: {}", routeId);
                }
        );
    }

    public RouterFunction<ServerResponse> buildRouterFunction() {
        com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = getNativeCache();
        List<RouteRecord> routes = nativeCache.asMap().values().stream()
                .filter(v -> v instanceof RouteRecord)
                .map(v -> (RouteRecord) v)
                .filter(RouteRecord::isActive)
                .sorted((a, b) -> Integer.compare(a.getPriority(), b.getPriority()))
                .toList();

        if (routes.isEmpty()) {
            log.warn("[RouteLoader] 활성화된 라우팅이 없습니다.");
            return null;
        }

        RouterFunctions.Builder builder = RouterFunctions.route();
        routes.forEach(routeRecord -> builder.add(buildSingleRoute(routeRecord)));
        return builder.build();
    }

    private RouterFunction<ServerResponse> buildSingleRoute(RouteRecord routeRecord) {
        // 게이트웨이는 모든 메서드를 다운스트림으로 전달
        // 메서드 제한은 다운스트림 서비스 또는 API Key 스코프 필터에서 처리
        var builder = route(routeRecord.getRouteId())
                .route(RequestPredicates.all()
                        .and(RequestPredicates.path("/fallback/**").negate())
                        .and(RequestPredicates.path("/actuator/**").negate()),
                        http())
                .before(uri(URI.create(routeRecord.getDownstreamUri())));

        // 서킷브레이커 설정 적용 (circuit_breaker_config JSONB)
        applyCircuitBreaker(routeRecord, builder);

        // Retry 설정 적용 (retry_config JSONB)
        applyRetry(routeRecord, builder);

        return builder.build();
    }

    private void applyCircuitBreaker(RouteRecord routeRecord, RouterFunctions.Builder builder) {
        if (routeRecord.getCircuitBreakerConfig() == null) return;

        try {
            Map<String, Object> config = objectMapper.readValue(
                    routeRecord.getCircuitBreakerConfig(), new TypeReference<>() {});

            String cbName = (String) config.getOrDefault("name", routeRecord.getRouteId() + "CB");

            builder.filter(circuitBreaker(cb -> cb
                    .setId(cbName)
                    .setFallbackUri("forward:/fallback")
                    .setStatusCodes(Set.of(
                            String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()),
                            String.valueOf(HttpStatus.BAD_GATEWAY.value()),
                            String.valueOf(HttpStatus.SERVICE_UNAVAILABLE.value()),
                            String.valueOf(HttpStatus.GATEWAY_TIMEOUT.value())
                    ))
            ));

            log.debug("[RouteLoader] 서킷브레이커 적용 — routeId: {}, cbName: {}",
                    routeRecord.getRouteId(), cbName);
        } catch (Exception e) {
            log.error("[RouteLoader] 서킷브레이커 설정 파싱 오류 — routeId: {}", routeRecord.getRouteId(), e);
        }
    }

    private void applyRetry(RouteRecord routeRecord, RouterFunctions.Builder builder) {
        if (routeRecord.getRetryConfig() == null) return;

        try {
            Map<String, Object> config = objectMapper.readValue(
                    routeRecord.getRetryConfig(), new TypeReference<>() {});

            int retries = (int) config.getOrDefault("retries", 3);

            // GET만 재시도 (멱등성 없는 POST/PUT/DELETE 제외)
            builder.filter(retry(r -> r
                    .setRetries(retries)
                    .setSeries(Set.of(HttpStatus.Series.SERVER_ERROR))
                    .setMethods(Set.of(HttpMethod.GET))
                    .setCacheBody(true)
            ));

            log.debug("[RouteLoader] Retry 적용 — routeId: {}, retries: {}",
                    routeRecord.getRouteId(), retries);
        } catch (Exception e) {
            log.error("[RouteLoader] Retry 설정 파싱 오류 — routeId: {}", routeRecord.getRouteId(), e);
        }
    }

    // Spring Cache 추상화 — 단건 get/put/evict
    private Cache getCache() {
        Cache cache = cacheManager.getCache(CacheConfig.ROUTE_CACHE);
        if (cache == null) throw new IllegalStateException("routeCache를 찾을 수 없습니다.");
        return cache;
    }

    // Caffeine 네이티브 — 전체 키/값 목록 조회 시에만 사용
    private com.github.benmanes.caffeine.cache.Cache<Object, Object> getNativeCache() {
        CaffeineCache caffeineCache = (CaffeineCache) cacheManager.getCache(CacheConfig.ROUTE_CACHE);
        if (caffeineCache == null) throw new IllegalStateException("routeCache를 찾을 수 없습니다.");
        return caffeineCache.getNativeCache();
    }
}
