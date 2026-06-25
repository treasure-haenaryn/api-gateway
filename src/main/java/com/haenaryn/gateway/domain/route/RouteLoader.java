package com.haenaryn.gateway.domain.route;

import com.github.benmanes.caffeine.cache.Cache;
import com.haenaryn.gateway.cache.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
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

    // CacheManager에서 Caffeine 네이티브 Cache 꺼내는 헬퍼
    // Spring Cache 추상화 → Caffeine 네이티브 API 활용 가능
    @SuppressWarnings("unchecked")
    private Cache<Object, Object> getNativeCache() {
        CaffeineCache caffeineCache = (CaffeineCache) cacheManager.getCache(CacheConfig.ROUTE_CACHE);
        if (caffeineCache == null) {
            throw new IllegalStateException("routeCache를 찾을 수 없습니다.");
        }
        return caffeineCache.getNativeCache();
    }

    // 앱 기동 시 전체 라우팅 로드
    @Override
    public void run(ApplicationArguments args) {
        loadRoutes();
    }

    // Pub/Sub 유실 대비 안전망 — 30초마다 DB에서 전체 리로드
    @Scheduled(fixedDelayString = "${gateway.cache.route-reload-interval-ms:30000}")
    public void scheduledReload() {
        log.debug("[RouteLoader] 주기적 라우팅 캐시 리로드 시작");
        loadRoutes();
    }

    // 전체 라우팅 DB 조회 → 로컬 캐시 적재
    // → 항상 이전 데이터라도 서빙 가능한 상태 유지 (무중단 방식)
    public void loadRoutes() {
        List<RouteRecord> routes = routeRepository.findAllActive();
        Cache<Object, Object> nativeCache = getNativeCache();

        // DB에 있는 route_id 목록
        Set<String> freshIds = routes.stream()
                .map(RouteRecord::getRouteId)
                .collect(Collectors.toSet());

        // 캐시에 있지만 DB에 없는 항목 제거 (비활성화 또는 삭제된 라우팅)
        nativeCache.asMap().keySet().stream()
                .filter(key -> !freshIds.contains(key))
                .forEach(nativeCache::invalidate);

        // 전체 put() — 변경된 것도 교체, 신규 항목도 적재
        routes.forEach(r -> nativeCache.put(r.getRouteId(), r));

        log.info("[RouteLoader] 라우팅 캐시 로드 완료 — 총 {}개", routes.size());
    }

    // route_id로 단건 캐시 갱신 (Pub/Sub 이벤트 수신 시 호출)
    public void refreshRoute(String routeId) {
        Cache<Object, Object> nativeCache = getNativeCache();
        routeRepository.findByRouteId(routeId).ifPresentOrElse(
                routeRecord -> {
                    if (routeRecord.isActive()) {
                        nativeCache.put(routeId, routeRecord);
                        log.info("[RouteLoader] 라우팅 캐시 갱신 — routeId: {}", routeId);
                    } else {
                        nativeCache.invalidate(routeId);
                        log.info("[RouteLoader] 라우팅 캐시 제거 — routeId: {}", routeId);
                    }
                },
                () -> {
                    nativeCache.invalidate(routeId);
                    log.info("[RouteLoader] 라우팅 캐시 제거 (DB 없음) — routeId: {}", routeId);
                }
        );
    }

    // 캐시 기반 RouterFunction 동적 빌드
    // TODO predicates/filters JSON 파싱 구현 예정
    public RouterFunction<ServerResponse> buildRouterFunction() {
        Cache<Object, Object> nativeCache = getNativeCache();
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
        // TODO Phase 3에서 predicates/filters JSON 파싱 후 동적 적용 예정
        // 현재는 downstream URI로의 기본 라우팅만 처리
        return route(routeRecord.getRouteId())
                .GET("/**", http())
                .before(uri(URI.create(routeRecord.getDownstreamUri())))
                .build();
    }
}
