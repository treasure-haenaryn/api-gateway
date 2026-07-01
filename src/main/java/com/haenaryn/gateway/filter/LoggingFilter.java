package com.haenaryn.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Slf4j
@Component
@Order(0)
public class LoggingFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next)
            throws Exception {

        long startTime = System.currentTimeMillis();

        // MDC에 요청 컨텍스트 설정
        // → 이후 모든 로그에 자동 포함 (traceId는 OTel이 자동 설정)
        String clientIp = getClientIp(request);
        MDC.put("clientIp", clientIp);
        MDC.put("method", request.method().name());
        MDC.put("path", request.path());

        try {
            ServerResponse response = next.handle(request);

            long duration = System.currentTimeMillis() - startTime;

            String authType = resolveAuthType(request);
            String authSubject = resolveAuthSubject(request);

            log.info("[LoggingFilter] {} {} — ip: {}, auth: {}:{}, status: {}, duration: {}ms",
                    request.method().name(),
                    request.path(),
                    clientIp,
                    authType,
                    authSubject,
                    response.statusCode(),
                    duration
            );

            return response;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[LoggingFilter] {} {} — ip: {}, duration: {}ms, error: {}",
                    request.method().name(),
                    request.path(),
                    clientIp,
                    duration,
                    e.getMessage()
            );
            throw e;
        } finally {
            MDC.clear();
        }
    }

    private String resolveAuthType(ServerRequest request) {
        if (request.attributes().containsKey("apiKeyId")) {
            return "API_KEY";
        }
        if (request.attributes().containsKey("jwtSub")) {
            return "JWT";
        }
        return "NONE";
    }

    private String resolveAuthSubject(ServerRequest request) {
        String apiKeyPrefix = (String) request.attributes().get("apiKeyPrefix");
        if (apiKeyPrefix != null) {
            return apiKeyPrefix;
        }
        String jwtSub = (String) request.attributes().get("jwtSub");
        if (jwtSub != null) {
            return jwtSub;
        }
        return "-";
    }

    private String getClientIp(ServerRequest request) {
        String forwarded = request.headers().firstHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.remoteAddress()
                .map(addr -> addr.getAddress().getHostAddress())
                .orElse("unknown");
    }
}
