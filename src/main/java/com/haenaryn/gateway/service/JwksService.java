package com.haenaryn.gateway.service;

import com.haenaryn.gateway.config.JwtConfig;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.security.PublicKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwksService implements ApplicationRunner {

    private final JwtConfig jwtConfig;

    // kid(Key ID) → 공개키 로컬 캐시
    private final Map<String, PublicKey> publicKeyCache = new ConcurrentHashMap<>();

    @Override
    public void run(ApplicationArguments args) {
        loadJwks();
    }

    @Scheduled(fixedDelayString = "${gateway.jwt.jwks-refresh-interval-sec:3600}000")
    public void scheduledRefresh() {
        log.debug("[JwksService] JWKS 주기적 갱신 시작");
        loadJwks();
    }

    // kid로 공개키 조회
    // 캐시에 없으면 JWKS 재로드 시도 (키 교체 대응)
    public PublicKey getPublicKey(String kid) {
        PublicKey key = publicKeyCache.get(kid);
        if (key == null) {
            log.warn("[JwksService] 공개키 없음 — kid: {}, JWKS 재로드 시도", kid);
            loadJwks();
            key = publicKeyCache.get(kid);
        }
        return key;
    }

    public boolean hasPublicKeys() {
        return !publicKeyCache.isEmpty();
    }

    private void loadJwks() {
        try {
            // nimbus-jose-jwt가 JWKS 로드, 파싱, 키 변환을 모두 처리
            // → RFC 7517 (JWK) 완전 준수
            JWKSet jwkSet = JWKSet.load(new URL(jwtConfig.getJwksUri()));

            Map<String, PublicKey> newKeys = new ConcurrentHashMap<>();
            for (JWK jwk : jwkSet.getKeys()) {
                try {
                    if (jwk instanceof ECKey ecKey) {
                        String kid = ecKey.getKeyID();
                        PublicKey publicKey = ecKey.toECPublicKey();
                        newKeys.put(kid, publicKey);
                        log.debug("[JwksService] EC 공개키 로드 — kid: {}", kid);
                    }
                } catch (Exception e) {
                    log.error("[JwksService] 공개키 파싱 오류 — kid: {}", jwk.getKeyID(), e);
                }
            }

            if (!newKeys.isEmpty()) {
                publicKeyCache.clear();
                publicKeyCache.putAll(newKeys);
                log.info("[JwksService] JWKS 로드 완료 — 총 {}개 공개키", newKeys.size());
            } else {
                log.warn("[JwksService] JWKS에 유효한 EC 키 없음");
            }

        } catch (Exception e) {
            if (publicKeyCache.isEmpty()) {
                log.error("[JwksService] JWKS 로드 실패 — 캐시 없음, JWT 검증 불가", e);
            } else {
                log.warn("[JwksService] JWKS 로드 실패 — 기존 캐시로 계속 검증 ({}개 키)", publicKeyCache.size());
            }
        }
    }
}
