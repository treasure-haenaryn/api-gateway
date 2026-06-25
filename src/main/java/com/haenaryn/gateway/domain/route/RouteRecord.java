package com.haenaryn.gateway.domain.route;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RouteRecord {

    private final Long id;
    private final String routeId;
    private final String downstreamUri;
    private final int priority;
    private final String predicates;
    private final String filters;
    private final int connectTimeoutMs;
    private final int responseTimeoutMs;
    private final String retryConfig;
    private final String circuitBreakerConfig;
    private final String fallbackMessage;
    private final String metadata;
    private final boolean isActive;
    private final boolean isDeleted;
    private final String createdBy;
    private final LocalDateTime createdAt;
    private final String updatedBy;
    private final LocalDateTime updatedAt;
}
