package com.haenaryn.gateway.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class RateLimitConfig {

    @Value("${gateway.rate-limit.apikey-per-sec:100}")
    private int apikeyPerSec;

    @Value("${gateway.rate-limit.apikey-per-day:10000}")
    private int apikeyPerDay;

    @Value("${gateway.rate-limit.ip-per-sec:30}")
    private int ipPerSec;
}
