package com.haenaryn.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Configuration
public class CorsConfig {

    @Value("${gateway.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Value("${gateway.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
    private String allowedMethods;

    @Value("${gateway.cors.max-age:3600}")
    private long maxAge;

    // 게이트웨이에서 CORS 통합 관리
    // → 각 다운스트림 서비스가 CORS 설정할 필요 없음
    // → 허용 오리진 변경 시 게이트웨이만 수정
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // 허용 오리진 (환경변수로 외부화)
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        config.setAllowedOrigins(origins);
        log.info("[CorsConfig] 허용 오리진: {}", origins);

        // 허용 메서드
        config.setAllowedMethods(Arrays.asList(allowedMethods.split(",")));

        // 모든 헤더 허용
        config.setAllowedHeaders(List.of("*"));

        // 자격증명 포함 허용 (쿠키, Authorization 헤더)
        config.setAllowCredentials(true);

        // Preflight 캐싱 (OPTIONS 요청 반복 방지)
        config.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
