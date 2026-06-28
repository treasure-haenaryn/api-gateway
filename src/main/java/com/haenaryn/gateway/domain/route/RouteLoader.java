package com.haenaryn.gateway.domain.route;

import com.haenaryn.gateway.cache.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class RouteLoader implements ApplicationRunner {

    private final RouteRepository routeRepository;
    private final CacheManager cacheManager;

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

        com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = getNativeCache();
        Set<String> freshIds = routes.stream()
                .map(RouteRecord::getRouteId)
                .collect(Collectors.toSet());

        nativeCache.asMap().keySet().stream()
                .filter(key -> !freshIds.contains(key))
                .forEach(nativeCache::invalidate);

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
        return route(routeRecord.getRouteId())
                .GET("/**", http())
                .before(uri(URI.create(routeRecord.getDownstreamUri())))
                .build();
    }

    private Cache getCache() {
        Cache cache = cacheManager.getCache(CacheConfig.ROUTE_CACHE);
        if (cache == null) throw new IllegalStateException("routeCache를 찾을 수 없습니다.");
        return cache;
    }

    private com.github.benmanes.caffeine.cache.Cache<Object, Object> getNativeCache() {
        CaffeineCache caffeineCache = (CaffeineCache) cacheManager.getCache(CacheConfig.ROUTE_CACHE);
        if (caffeineCache == null) throw new IllegalStateException("routeCache를 찾을 수 없습니다.");
        return caffeineCache.getNativeCache();
    }
}
