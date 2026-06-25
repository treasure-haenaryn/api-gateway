package com.haenaryn.gateway.pubsub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEventPublisher {

    private final StringRedisTemplate redisTemplate;

    public static final String ROUTE_UPDATED_CHANNEL = "gateway:routes:updated";

    public void publishRouteUpdated(String routeId) {
        redisTemplate.convertAndSend(ROUTE_UPDATED_CHANNEL, routeId);
        log.info("[CacheEventPublisher] 라우팅 변경 이벤트 발행 — routeId: {}", routeId);
    }
}
