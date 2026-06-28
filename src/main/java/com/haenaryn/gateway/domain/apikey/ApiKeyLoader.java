package com.haenaryn.gateway.domain.apikey;

import com.haenaryn.gateway.cache.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyLoader implements ApplicationRunner {

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyScopeRepository apiKeyScopeRepository;
    private final CacheManager cacheManager;

    @Override
    public void run(ApplicationArguments args) {
        loadApiKeys();
    }

    @Scheduled(fixedDelayString = "${gateway.cache.route-reload-interval-ms:30000}")
    public void scheduledReload() {
        log.debug("[ApiKeyLoader] 주기적 API Key 캐시 리로드 시작");
        loadApiKeys();
    }

    public void loadApiKeys() {
        loadApiKeyCache();
        loadApiScopeCache();
        log.info("[ApiKeyLoader] API Key 캐시 로드 완료");
    }

    private void loadApiKeyCache() {
        List<ApiKeyRecord> apiKeys = apiKeyRepository.findAllActive();

        com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache =
                getNativeCache(CacheConfig.API_KEY_CACHE);
        Set<String> freshHashes = apiKeys.stream()
                .map(ApiKeyRecord::getKeyHash)
                .collect(Collectors.toSet());

        nativeCache.asMap().keySet().stream()
                .filter(key -> !freshHashes.contains(key))
                .forEach(nativeCache::invalidate);

        Cache cache = getCache(CacheConfig.API_KEY_CACHE);
        apiKeys.forEach(k -> cache.put(k.getKeyHash(), k));

        log.info("[ApiKeyLoader] API Key 캐시 로드 완료 — 총 {}개", apiKeys.size());
    }

    private void loadApiScopeCache() {
        List<ApiKeyScopeRecord> scopes = apiKeyScopeRepository.findAllActive();

        Map<Long, List<ApiKeyScopeRecord>> scopeMap = scopes.stream()
                .collect(Collectors.groupingBy(ApiKeyScopeRecord::getApiKeyId));

        com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache =
                getNativeCache(CacheConfig.API_SCOPE_CACHE);
        Set<Object> freshIds = scopeMap.keySet().stream()
                .map(id -> (Object) id)
                .collect(Collectors.toSet());

        nativeCache.asMap().keySet().stream()
                .filter(key -> !freshIds.contains(key))
                .forEach(nativeCache::invalidate);

        Cache cache = getCache(CacheConfig.API_SCOPE_CACHE);
        scopeMap.forEach(cache::put);

        log.info("[ApiKeyLoader] API Key 스코프 캐시 로드 완료 — 총 {}개", scopes.size());
    }

    public void refreshApiKey(String keyHash) {
        Cache cache = getCache(CacheConfig.API_KEY_CACHE);
        apiKeyRepository.findByKeyHash(keyHash).ifPresentOrElse(
                apiKey -> {
                    if (apiKey.isActive()) {
                        cache.put(keyHash, apiKey);
                        refreshApiScope(apiKey.getId());
                        log.info("[ApiKeyLoader] API Key 캐시 갱신 — keyHash: {}...", keyHash.substring(0, 8));
                    } else {
                        cache.evict(keyHash);
                        getCache(CacheConfig.API_SCOPE_CACHE).evict(apiKey.getId());
                        log.info("[ApiKeyLoader] API Key 캐시 제거 — keyHash: {}...", keyHash.substring(0, 8));
                    }
                },
                () -> {
                    cache.evict(keyHash);
                    log.info("[ApiKeyLoader] API Key 캐시 제거 (DB 없음) — keyHash: {}...", keyHash.substring(0, 8));
                }
        );
    }

    private void refreshApiScope(Long apiKeyId) {
        Cache cache = getCache(CacheConfig.API_SCOPE_CACHE);
        List<ApiKeyScopeRecord> scopes = apiKeyScopeRepository.findByApiKeyId(apiKeyId);
        if (scopes.isEmpty()) {
            cache.evict(apiKeyId);
        } else {
            cache.put(apiKeyId, scopes);
        }
    }

    private Cache getCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) throw new IllegalStateException(cacheName + " 캐시를 찾을 수 없습니다.");
        return cache;
    }

    private com.github.benmanes.caffeine.cache.Cache<Object, Object> getNativeCache(String cacheName) {
        CaffeineCache caffeineCache = (CaffeineCache) cacheManager.getCache(cacheName);
        if (caffeineCache == null) throw new IllegalStateException(cacheName + " 캐시를 찾을 수 없습니다.");
        return caffeineCache.getNativeCache();
    }
}
