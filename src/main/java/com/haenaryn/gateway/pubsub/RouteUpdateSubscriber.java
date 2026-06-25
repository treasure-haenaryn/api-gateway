package com.haenaryn.gateway.pubsub;

import com.haenaryn.gateway.domain.route.RouteLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RouteUpdateSubscriber implements MessageListener {

    private final RouteLoader routeLoader;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String routeId = new String(message.getBody());
        log.info("[RouteUpdateSubscriber] 라우팅 변경 이벤트 수신 — routeId: {}", routeId);
        routeLoader.refreshRoute(routeId);
    }
}
