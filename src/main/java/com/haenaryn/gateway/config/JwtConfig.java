package com.haenaryn.gateway.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class JwtConfig {

    @Value("${gateway.jwt.jwks-uri:http://localhost:9000/.well-known/jwks.json}")
    private String jwksUri;

    @Value("${gateway.jwt.jwks-refresh-interval-sec:3600}")
    private int jwksRefreshIntervalSec;

    @Value("${gateway.jwt.blacklist-enabled:true}")
    private boolean blacklistEnabled;
}
