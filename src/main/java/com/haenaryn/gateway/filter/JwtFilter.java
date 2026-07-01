package com.haenaryn.gateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Slf4j
@Component
@Order(5)
@RequiredArgsConstructor
public class JwtFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    private final StringRedisTemplate redisTemplate;

    @Value("${gateway.jwt.blacklist-enabled:true}")
    private boolean blacklistEnabled;

    private static final String BLACKLIST_KEY_PREFIX = "gateway:jwt:blacklist:";

    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next)
            throws Exception {

        if (request.path().startsWith("/fallback") || request.path().startsWith("/actuator")) {
            return next.handle(request);
        }

        if (request.attributes().containsKey("apiKeyId")) {
            return next.handle(request);
        }

        String authHeader = request.headers().firstHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return next.handle(request);
        }

        String token = authHeader.substring(7);

        // TODO: JWT 서명 검증 구현 예정 (JWKS 연동)
        if (blacklistEnabled && isBlacklisted(token)) {
            log.warn("[JwtFilter] 블랙리스트 토큰 감지 — path: {}", request.path());
            return ServerResponse.status(HttpStatus.UNAUTHORIZED).build();
        }

        // TODO: JWT 서명 검증 및 사용자 정보 헤더 주입 구현 예정

        return next.handle(request);
    }

    // Redis 블랙리스트 조회
    // JTI(JWT ID)를 키로 조회 → 존재하면 무효화된 토큰
    private boolean isBlacklisted(String token) {
        try {
            // TODO: 실제 구현 시 JWT 파싱 후 JTI 추출
            String key = BLACKLIST_KEY_PREFIX + token;
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error("[JwtFilter] Redis 블랙리스트 조회 오류 — 스킵", e);
            return false;
        }
    }
}
