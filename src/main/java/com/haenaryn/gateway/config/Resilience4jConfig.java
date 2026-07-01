package com.haenaryn.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class Resilience4jConfig {

    // 서킷브레이커 기본 설정
    // 라우팅별 개별 설정은 DB의 circuit_breaker_config (JSONB)에서 로드 예정
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultCircuitBreakerCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        // 실패율 50% 초과 시 OPEN
                        .failureRateThreshold(50)
                        // OPEN 유지 시간 — 이후 HALF_OPEN으로 전환
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        // 최근 10개 요청 기준으로 실패율 계산
                        .slidingWindowSize(10)
                        // HALF_OPEN 상태에서 허용할 테스트 요청 수
                        .permittedNumberOfCallsInHalfOpenState(3)
                        // 서킷브레이커를 오픈시킬 HTTP 상태 코드
                        .recordException(e -> true)
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        // 타임아웃 (라우팅별 response_timeout_ms로 재정의 예정)
                        .timeoutDuration(Duration.ofSeconds(10))
                        .build())
                .build());
    }
}
