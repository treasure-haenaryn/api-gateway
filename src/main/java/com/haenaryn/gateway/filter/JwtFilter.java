package com.haenaryn.gateway.filter;

import com.haenaryn.gateway.config.JwtConfig;
import com.haenaryn.gateway.service.JwksService;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.security.interfaces.ECPublicKey;
import java.util.Date;

@Slf4j
@Component
@Order(5)
@RequiredArgsConstructor
public class JwtFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    private final StringRedisTemplate redisTemplate;
    private final JwksService jwksService;
    private final JwtConfig jwtConfig;

    private static final String BLACKLIST_KEY_PREFIX = "gateway:jwt:blacklist:";

    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next)
            throws Exception {

        // /fallback, /actuator 경로는 JWT 검증 스킵
        if (request.path().startsWith("/fallback") || request.path().startsWith("/actuator") || request.path().startsWith("/error")) {
            return next.handle(request);
        }

        // API Key 인증을 거친 요청은 JWT 검증 스킵
        // API Key 와 JWT는 상호 배타적 인증 방식
        if (request.attributes().containsKey("apiKeyId")) {
            return next.handle(request);
        }

        String authHeader = request.headers().firstHeader("Authorization");

        // Authorization 헤더 없으면 스킵 (공개 API)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return next.handle(request);
        }

        String token = authHeader.substring(7);

        // 1. JWT 파싱 (nimbus-jose-jwt)
        SignedJWT signedJWT;
        try {
            signedJWT = SignedJWT.parse(token);
        } catch (Exception e) {
            log.warn("[JwtFilter] JWT 파싱 실패 — path: {}", request.path());
            return ServerResponse.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 2. kid 추출 (서명에 사용된 공개키 식별자)
        String kid = signedJWT.getHeader().getKeyID();
        if (kid == null) {
            log.warn("[JwtFilter] JWT kid 없음 — path: {}", request.path());
            return ServerResponse.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 3. 공개키 조회 (로컬 캐시 → JWKS 재로드)
        ECPublicKey publicKey = (ECPublicKey) jwksService.getPublicKey(kid);
        if (publicKey == null) {
            log.warn("[JwtFilter] 공개키 없음 — kid: {}", kid);
            return ServerResponse.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 4. ES256 서명 검증 (nimbus-jose-jwt ECDSAVerifier)
        // → RFC 7517 준수, 보안 검증된 구현체 사용
        try {
            JWSVerifier verifier = new ECDSAVerifier(publicKey);
            if (!signedJWT.verify(verifier)) {
                log.warn("[JwtFilter] JWT 서명 검증 실패 — path: {}", request.path());
                return ServerResponse.status(HttpStatus.UNAUTHORIZED).build();
            }
        } catch (Exception e) {
            log.warn("[JwtFilter] JWT 서명 검증 오류 — path: {}, reason: {}", request.path(), e.getMessage());
            return ServerResponse.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 5. Claims 추출 및 만료 검증
        JWTClaimsSet claims;
        try {
            claims = signedJWT.getJWTClaimsSet();
        } catch (Exception e) {
            log.warn("[JwtFilter] JWT Claims 파싱 실패 — path: {}", request.path());
            return ServerResponse.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 만료 검증
        Date expiration = claims.getExpirationTime();
        if (expiration == null || expiration.before(new Date())) {
            log.warn("[JwtFilter] JWT 만료 — sub: {}, path: {}", claims.getSubject(), request.path());
            return ServerResponse.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 6. 블랙리스트 확인 (JTI 기반)
        if (jwtConfig.isBlacklistEnabled()) {
            String jti = claims.getJWTID();
            if (jti != null && isBlacklisted(jti)) {
                log.warn("[JwtFilter] 블랙리스트 토큰 — jti: {}, path: {}", jti, request.path());
                return ServerResponse.status(HttpStatus.UNAUTHORIZED).build();
            }
        }

        // 7. 검증 완료 — 사용자 정보를 요청 속성에 저장
        // SecurityHeaderFilter에서 외부 내부 헤더를 이미 제거했으므로 안전하게 주입 가능
        request.attributes().put("jwtSub", claims.getSubject());
        request.attributes().put("jwtRole", claims.getStringClaim("role"));
        request.attributes().put("jwtEmail", claims.getStringClaim("email"));

        log.debug("[JwtFilter] JWT 검증 성공 — sub: {}, path: {}", claims.getSubject(), request.path());
        return next.handle(request);
    }

    // Redis 블랙리스트 조회 (JTI 기반)
    // TTL = 토큰 남은 만료 시간 → 토큰 만료 시 Redis에서도 자동 삭제
    private boolean isBlacklisted(String jti) {
        try {
            Boolean exists = redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + jti);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            // Redis 장애 시 관용적 정책 → 블랙리스트 스킵 (서비스 중단 방지)
            log.error("[JwtFilter] Redis 블랙리스트 조회 오류 — 스킵", e);
            return false;
        }
    }
}
