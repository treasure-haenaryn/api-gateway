package com.haenaryn.gateway.controller;

import com.haenaryn.gateway.cache.CacheConfig;
import com.haenaryn.gateway.domain.route.RouteRecord;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@RestController
@RequiredArgsConstructor
public class FallbackController {

    private final CacheManager cacheManager;

    // 서킷브레이커 오픈 시 폴백 엔드포인트
    // 폴백 메시지는 DB(캐시)에서 동적으로 조회
    // → DB 변경 + Redis Pub/Sub → 캐시 갱신 → 재배포 없이 즉시 반영
    @RequestMapping("/fallback")
    public ResponseEntity<FallbackResponse> fallback(HttpServletRequest request) {
        String routeId = request.getHeader("X-Gateway-Route-Id");

        String message = resolveFallbackMessage(routeId);

        log.warn("[FallbackController] 서킷브레이커 폴백 — routeId: {}", routeId);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(FallbackResponse.builder()
                        .status("SERVICE_UNAVAILABLE")
                        .routeId(routeId)
                        .message(message)
                        .build());
    }

    // 라우팅 캐시에서 폴백 메시지 조회
    // 없으면 기본 메시지 반환
    private String resolveFallbackMessage(String routeId) {
        if (routeId == null) {
            return "서비스가 일시적으로 사용할 수 없습니다.";
        }

        Cache cache = cacheManager.getCache(CacheConfig.ROUTE_CACHE);
        if (cache == null) {
            return "서비스가 일시적으로 사용할 수 없습니다.";
        }

        RouteRecord route = cache.get(routeId, RouteRecord.class);
        if (route == null || route.getFallbackMessage() == null) {
            return "서비스가 일시적으로 사용할 수 없습니다.";
        }

        return route.getFallbackMessage();
    }

    @Getter
    @Builder
    public static class FallbackResponse {
        private final String status;
        private final String routeId;
        private final String message;
    }
}
