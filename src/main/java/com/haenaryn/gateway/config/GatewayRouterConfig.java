package com.haenaryn.gateway.config;

import com.haenaryn.gateway.domain.route.RouteLoader;
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

    // Spring MVC에 RouterFunction 등록
    // DB에 라우팅 데이터가 없어도 앱이 기동되도록
    // 라우팅이 없으면 404를 반환하는 기본 라우트로 대체
    @Bean
    public RouterFunction<ServerResponse> gatewayRouterFunction() {
        // DB에서 직접 로드 (캐시 타이밍 문제 회피)
        routeLoader.loadRoutes();
        RouterFunction<ServerResponse> routerFunction = routeLoader.buildRouterFunction();

        if (routerFunction == null) {
            log.warn("[GatewayRouterConfig] 등록된 라우팅이 없습니다. DB에 라우팅 데이터를 추가하세요.");
            // 라우팅 없을 때 404 반환하는 기본 라우트
            return RouterFunctions.route()
                    .GET("/**", request -> ServerResponse.notFound().build())
                    .POST("/**", request -> ServerResponse.notFound().build())
                    .build();
        }

        log.info("[GatewayRouterConfig] RouterFunction 등록 완료");
        return routerFunction;
    }
}
