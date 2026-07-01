package com.haenaryn.gateway.filter;

import com.haenaryn.gateway.config.RateLimitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@Order(4)
@RequiredArgsConstructor
public class RateLimitFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    private final StringRedisTemplate redisTemplate;
    private final RateLimitConfig rateLimitConfig;

    // Race Condition 방지
    private static final String RATE_LIMIT_SCRIPT = """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """;

    private static final DefaultRedisScript<Long> SCRIPT =
            new DefaultRedisScript<>(RATE_LIMIT_SCRIPT, Long.class);

    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next)
            throws Exception {

        if (request.path().startsWith("/fallback") || request.path().startsWith("/actuator")) {
            return next.handle(request);
        }

        String clientIp = getClientIp(request);
        String apiKeyPrefix = (String) request.attributes().get("apiKeyPrefix");

        // 1. IP별 초당 제한 확인
        if (!checkRateLimit(
                "gateway:ratelimit:ip:" + clientIp + ":sec:" + Instant.now().getEpochSecond(),
                rateLimitConfig.getIpPerSec(),
                1)) {
            log.warn("[RateLimitFilter] IP 초당 제한 초과 — ip: {}", clientIp);
            return buildRateLimitResponse(rateLimitConfig.getIpPerSec(), 0, 1);
        }

        // 2. API Key별 초당 제한 확인
        if (apiKeyPrefix != null) {
            String apiKeyHash = (String) request.attributes().get("apiKeyId");

            if (!checkRateLimit(
                    "gateway:ratelimit:apikey:" + apiKeyHash + ":sec:" + Instant.now().getEpochSecond(),
                    rateLimitConfig.getApikeyPerSec(),
                    1)) {
                log.warn("[RateLimitFilter] API Key 초당 제한 초과 — prefix: {}", apiKeyPrefix);
                return buildRateLimitResponse(rateLimitConfig.getApikeyPerSec(), 0, 1);
            }

            // 3. API Key별 일별 제한 확인
            String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            if (!checkRateLimit(
                    "gateway:ratelimit:apikey:" + apiKeyHash + ":day:" + today,
                    rateLimitConfig.getApikeyPerDay(),
                    86400)) {
                log.warn("[RateLimitFilter] API Key 일별 제한 초과 — prefix: {}", apiKeyPrefix);
                return buildRateLimitResponse(rateLimitConfig.getApikeyPerDay(), 0, 86400);
            }
        }

        return next.handle(request);
    }

    // Lua 스크립트로 원자적 카운트 증가 후 제한값 비교
    private boolean checkRateLimit(String key, int limit, int ttlSeconds) {
        try {
            Long count = redisTemplate.execute(
                    SCRIPT,
                    List.of(key),
                    String.valueOf(ttlSeconds)
            );
            return count != null && count <= limit;
        } catch (Exception e) {
            // Redis 장애 시 관용적 정책 → 통과 (서비스 중단 방지)
            log.error("[RateLimitFilter] Redis 오류 — Rate Limit 스킵", e);
            return true;
        }
    }

    private ServerResponse buildRateLimitResponse(int limit, int remaining, int resetSeconds) {
        long resetTime = Instant.now().getEpochSecond() + resetSeconds;
        return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("X-RateLimit-Limit", String.valueOf(limit))
                .header("X-RateLimit-Remaining", String.valueOf(remaining))
                .header("X-RateLimit-Reset", String.valueOf(resetTime))
                .header("Retry-After", String.valueOf(resetSeconds))
                .build();
    }

    private String getClientIp(ServerRequest request) {
        // X-Forwarded-For 헤더 우선 (로드밸런서 뒤에 있을 경우)
        String forwarded = request.headers().firstHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.remoteAddress()
                .map(addr -> addr.getAddress().getHostAddress())
                .orElse("unknown");
    }
}
