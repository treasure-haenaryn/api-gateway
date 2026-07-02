package com.haenaryn.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.List;

@Slf4j
@Component
@Order(1)
public class SecurityHeaderFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    // 클라이언트가 위조할 수 없도록 제거할 내부 전용 헤더 목록
    // 게이트웨이가 검증 후 직접 주입한 헤더만 다운스트림에 전달
    private static final List<String> INTERNAL_HEADERS = List.of(
            "X-User-Id",
            "X-User-Role",
            "X-User-Email",
            "X-Internal-Token"
    );

    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next)
            throws Exception {

        if (request.path().startsWith("/fallback") || request.path().startsWith("/actuator") || request.path().startsWith("/error")) {
            return next.handle(request);
        }

        // 내부 헤더 포함 여부 로깅 (운영 시 위조 시도 감지용)
        INTERNAL_HEADERS.forEach(header -> {
            if (request.headers().firstHeader(header) != null) {
                log.warn("[SecurityHeaderFilter] 내부 헤더 위조 시도 감지 — header: {}, ip: {}",
                        header, request.remoteAddress().map(Object::toString).orElse("unknown"));
            }
        });

        // 내부 헤더 제거 후 재구성
        ServerRequest sanitized = ServerRequest.from(request)
                .headers(headers -> INTERNAL_HEADERS.forEach(headers::remove))
                .build();

        return next.handle(sanitized);
    }
}
