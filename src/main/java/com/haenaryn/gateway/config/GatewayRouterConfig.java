package com.haenaryn.gateway.config;

import com.haenaryn.gateway.domain.route.RouteLoader;
import com.haenaryn.gateway.filter.ApiKeyFilter;
import com.haenaryn.gateway.filter.ApiKeyScopeFilter;
import com.haenaryn.gateway.filter.JwtFilter;
import com.haenaryn.gateway.filter.LoggingFilter;
import com.haenaryn.gateway.filter.MetricsFilter;
import com.haenaryn.gateway.filter.RateLimitFilter;
import com.haenaryn.gateway.filter.SecurityHeaderFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class GatewayRouterConfig {

    private final RouteLoader routeLoader;
    private final LoggingFilter loggingFilter;
    private final MetricsFilter metricsFilter;
    private final SecurityHeaderFilter securityHeaderFilter;
    private final ApiKeyFilter apiKeyFilter;
    private final ApiKeyScopeFilter apiKeyScopeFilter;
    private final RateLimitFilter rateLimitFilter;
    private final JwtFilter jwtFilter;

    @Bean
    public RouterFunction<ServerResponse> gatewayRouterFunction() {
        routeLoader.loadRoutes();
        RouterFunction<ServerResponse> routerFunction = routeLoader.buildRouterFunction();

        if (routerFunction == null) {
            log.warn("[GatewayRouterConfig] 등록된 라우팅이 없습니다. DB에 라우팅 데이터를 추가하세요.");
            return RouterFunctions.route()
                    .GET("/**", request -> ServerResponse.notFound().build())
                    .POST("/**", request -> ServerResponse.notFound().build())
                    .build();
        }

        // 필터 체인 등록
        // .filter() 체이닝은 마지막에 등록된 필터가 가장 먼저 실행됨 (역순)
        // 원하는 실행 순서의 반대로 등록해야 함
        RouterFunction<ServerResponse> filtered = routerFunction
                .filter(jwtFilter)           
                .filter(rateLimitFilter)
                .filter(apiKeyScopeFilter)
                .filter(apiKeyFilter)
                .filter(securityHeaderFilter)
                .filter(metricsFilter)
                .filter(loggingFilter);

        log.info("[GatewayRouterConfig] RouterFunction 등록 완료");
        return filtered;
    }
}
