package com.haenaryn.gateway.filter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class MetricsFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    private final MeterRegistry meterRegistry;

    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next)
            throws Exception {

        if (request.path().startsWith("/fallback") || request.path().startsWith("/actuator")) {
            return next.handle(request);
        }

        long startTime = System.currentTimeMillis();
        String routeId = (String) request.attributes().get("routeId");
        if (routeId == null) routeId = "unknown";

        try {
            ServerResponse response = next.handle(request);
            long duration = System.currentTimeMillis() - startTime;

            recordRouteDuration(routeId, String.valueOf(response.statusCode().value()), duration);

            return response;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            recordRouteDuration(routeId, "500", duration);
            throw e;
        }
    }

    // 라우팅별 요청 소요 시간 기록
    // → Grafana에서 라우팅별 응답 시간, 상태 코드별 분포 확인 가능
    private void recordRouteDuration(String routeId, String status, long durationMs) {
        Timer.builder("gateway.route.duration")
                .description("라우팅별 요청 소요 시간")
                .tag("route_id", routeId)
                .tag("status", status)
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }
}
