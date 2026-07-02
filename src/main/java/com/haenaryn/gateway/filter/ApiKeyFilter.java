package com.haenaryn.gateway.filter;

import com.haenaryn.gateway.cache.CacheConfig;
import com.haenaryn.gateway.domain.apikey.ApiKeyRecord;
import com.haenaryn.gateway.domain.apikey.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Component
@Order(2)
public class ApiKeyFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    private final CacheManager cacheManager;
    private final ApiKeyRepository apiKeyRepository;
    private final Executor dbTaskExecutor;

    public ApiKeyFilter(
            CacheManager cacheManager,
            ApiKeyRepository apiKeyRepository,
            @Qualifier("dbTaskExecutor") Executor dbTaskExecutor
    ) {
        this.cacheManager = cacheManager;
        this.apiKeyRepository = apiKeyRepository;
        this.dbTaskExecutor = dbTaskExecutor;
    }

    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next)
            throws Exception {

        if (request.path().startsWith("/fallback") || request.path().startsWith("/actuator") || request.path().startsWith("/error")) {
            return next.handle(request);
        }

        String rawKey = request.headers().firstHeader("X-API-Key");
        if (rawKey == null || rawKey.isBlank()) {
            log.debug("[ApiKeyFilter] API Key 헤더 없음 — path: {}", request.path());
            return ServerResponse.status(HttpStatus.UNAUTHORIZED).build();
        }

        String keyHash = sha256(rawKey);

        Cache cache = cacheManager.getCache(CacheConfig.API_KEY_CACHE);
        if (cache == null) {
            log.error("[ApiKeyFilter] API Key 캐시를 찾을 수 없음");
            return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        ApiKeyRecord apiKey = cache.get(keyHash, ApiKeyRecord.class);
        if (apiKey == null) {
            log.warn("[ApiKeyFilter] 유효하지 않은 API Key — prefix: {}",
                    rawKey.substring(0, Math.min(8, rawKey.length())));
            return ServerResponse.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!apiKey.isActive()) {
            log.warn("[ApiKeyFilter] 비활성화된 API Key — keyPrefix: {}", apiKey.getKeyPrefix());
            return ServerResponse.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("[ApiKeyFilter] 만료된 API Key — keyPrefix: {}", apiKey.getKeyPrefix());
            return ServerResponse.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!isIpAllowed(request, apiKey)) {
            log.warn("[ApiKeyFilter] IP 허용 목록 외 접근 — keyPrefix: {}", apiKey.getKeyPrefix());
            return ServerResponse.status(HttpStatus.FORBIDDEN).build();
        }

        updateLastUsedAtIfNeeded(apiKey, cache);

        request.attributes().put("apiKeyId", apiKey.getId());
        request.attributes().put("apiKeyPrefix", apiKey.getKeyPrefix());

        log.debug("[ApiKeyFilter] API Key 검증 성공 — keyPrefix: {}", apiKey.getKeyPrefix());
        return next.handle(request);
    }

    private boolean isIpAllowed(ServerRequest request, ApiKeyRecord apiKey) {
        if (apiKey.getIpWhitelist() == null || apiKey.getIpWhitelist().isEmpty()) {
            return true;
        }
        String clientIp = request.remoteAddress()
                .map(addr -> addr.getAddress().getHostAddress())
                .orElse("");
        return apiKey.getIpWhitelist().contains(clientIp);
    }

    private void updateLastUsedAtIfNeeded(ApiKeyRecord apiKey, Cache cache) {
        if (apiKey.getLastUsedAt() != null &&
                apiKey.getLastUsedAt().isAfter(LocalDateTime.now().minusHours(1))) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                apiKeyRepository.updateLastUsedAt(apiKey.getId());

                ApiKeyRecord updated = ApiKeyRecord.builder()
                        .id(apiKey.getId())
                        .keyPrefix(apiKey.getKeyPrefix())
                        .keyHash(apiKey.getKeyHash())
                        .name(apiKey.getName())
                        .description(apiKey.getDescription())
                        .owner(apiKey.getOwner())
                        .keyType(apiKey.getKeyType())
                        .ipWhitelist(apiKey.getIpWhitelist())
                        .rateLimitPerSec(apiKey.getRateLimitPerSec())
                        .rateLimitPerDay(apiKey.getRateLimitPerDay())
                        .rateLimitPerMonth(apiKey.getRateLimitPerMonth())
                        .expiresAt(apiKey.getExpiresAt())
                        .lastUsedAt(LocalDateTime.now())
                        .revokedAt(apiKey.getRevokedAt())
                        .revokedBy(apiKey.getRevokedBy())
                        .isActive(apiKey.isActive())
                        .isDeleted(apiKey.isDeleted())
                        .createdBy(apiKey.getCreatedBy())
                        .createdAt(apiKey.getCreatedAt())
                        .updatedBy(apiKey.getUpdatedBy())
                        .updatedAt(apiKey.getUpdatedAt())
                        .build();
                cache.put(apiKey.getKeyHash(), updated);

                log.debug("[ApiKeyFilter] last_used_at 업데이트 — keyPrefix: {}", apiKey.getKeyPrefix());
            } catch (Exception e) {
                log.error("[ApiKeyFilter] last_used_at 업데이트 오류 — keyPrefix: {}", apiKey.getKeyPrefix(), e);
            }
        }, dbTaskExecutor);
    }

    private String sha256(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 찾을 수 없습니다.", e);
        }
    }
}
