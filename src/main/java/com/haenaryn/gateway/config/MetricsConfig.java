package com.haenaryn.gateway.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter rateLimitBlockedByIp(MeterRegistry meterRegistry) {
        return Counter.builder("gateway.rate_limit.blocked")
                .description("Rate Limit으로 차단된 요청 수")
                .tag("type", "ip")
                .register(meterRegistry);
    }

    @Bean
    public Counter rateLimitBlockedByApiKey(MeterRegistry meterRegistry) {
        return Counter.builder("gateway.rate_limit.blocked")
                .description("Rate Limit으로 차단된 요청 수")
                .tag("type", "apikey")
                .register(meterRegistry);
    }

    @Bean
    public Counter authFailedInvalidKey(MeterRegistry meterRegistry) {
        return Counter.builder("gateway.auth.failed")
                .description("인증 실패 수")
                .tag("reason", "invalid_key")
                .register(meterRegistry);
    }

    @Bean
    public Counter authFailedExpiredJwt(MeterRegistry meterRegistry) {
        return Counter.builder("gateway.auth.failed")
                .description("인증 실패 수")
                .tag("reason", "expired_jwt")
                .register(meterRegistry);
    }

    @Bean
    public Counter authFailedBlacklisted(MeterRegistry meterRegistry) {
        return Counter.builder("gateway.auth.failed")
                .description("인증 실패 수")
                .tag("reason", "blacklisted")
                .register(meterRegistry);
    }
}
