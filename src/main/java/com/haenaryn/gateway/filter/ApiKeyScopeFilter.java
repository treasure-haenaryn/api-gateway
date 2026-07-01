package com.haenaryn.gateway.filter;

import com.haenaryn.gateway.cache.CacheConfig;
import com.haenaryn.gateway.domain.apikey.ApiKeyScopeRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.List;

@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class ApiKeyScopeFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    private final CacheManager cacheManager;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next)
            throws Exception {

        Long apiKeyId = (Long) request.attributes().get("apiKeyId");

        if (request.path().startsWith("/fallback") || request.path().startsWith("/actuator")) {
            return next.handle(request);
        }

        if (apiKeyId == null) {
            // API Key 인증을 거치지 않은 요청 → 스코프 검증 스킵
            return next.handle(request);
        }

        List<ApiKeyScopeRecord> scopes = getScopesFromCache(apiKeyId);
        if (scopes == null || scopes.isEmpty()) {
            log.warn("[ApiKeyScopeFilter] 스코프 없음 — apiKeyId: {}", apiKeyId);
            return ServerResponse.status(HttpStatus.FORBIDDEN).build();
        }

        String requestPath   = request.path();
        String requestMethod = request.method().name();

        boolean allowed = scopes.stream()
                .filter(ApiKeyScopeRecord::isActive)
                .anyMatch(scope ->
                        pathMatcher.match(scope.getPathPattern(), requestPath) &&
                        scope.getAllowedMethods().contains(requestMethod)
                );

        if (!allowed) {
            log.warn("[ApiKeyScopeFilter] 스코프 미허용 — apiKeyId: {}, path: {}, method: {}",
                    apiKeyId, requestPath, requestMethod);
            return ServerResponse.status(HttpStatus.FORBIDDEN).build();
        }

        log.debug("[ApiKeyScopeFilter] 스코프 검증 성공 — path: {}, method: {}", requestPath, requestMethod);
        return next.handle(request);
    }

    private List<ApiKeyScopeRecord> getScopesFromCache(Long apiKeyId) {
        Cache cache = cacheManager.getCache(CacheConfig.API_SCOPE_CACHE);
        if (cache == null) return null;
        return (List<ApiKeyScopeRecord>) cache.get(apiKeyId, Object.class);
    }
}
