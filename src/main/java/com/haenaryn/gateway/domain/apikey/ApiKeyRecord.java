package com.haenaryn.gateway.domain.apikey;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ApiKeyRecord {

    private final Long id;
    private final String keyPrefix;
    private final String keyHash;
    private final String name;
    private final String description;
    private final String owner;
    private final ApiKeyType keyType;
    private final List<String> ipWhitelist; // null 이면 전체 허용
    private final Integer rateLimitPerSec;
    private final Integer rateLimitPerDay;
    private final Integer rateLimitPerMonth;
    private final LocalDateTime expiresAt;
    private final LocalDateTime lastUsedAt;
    private final LocalDateTime revokedAt;
    private final String revokedBy;
    private final boolean isActive;
    private final boolean isDeleted;
    private final String createdBy;
    private final LocalDateTime createdAt;
    private final String updatedBy;
    private final LocalDateTime updatedAt;
}
