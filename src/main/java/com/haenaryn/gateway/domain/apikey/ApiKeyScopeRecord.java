package com.haenaryn.gateway.domain.apikey;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ApiKeyScopeRecord {

    private final Long id;
    private final Long apiKeyId;
    private final String pathPattern;          // /api/orders/**
    private final List<String> allowedMethods; // ["GET", "POST"]
    private final boolean isActive;
}
