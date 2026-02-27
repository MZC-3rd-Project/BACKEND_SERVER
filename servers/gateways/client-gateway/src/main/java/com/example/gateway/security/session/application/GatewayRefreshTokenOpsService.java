package com.example.gateway.security.session.application;

import com.example.gateway.security.session.application.port.GatewayRefreshTokenFamilyRepository;
import com.example.gateway.security.session.domain.GatewayRefreshRotateRequest;
import com.example.gateway.security.session.domain.GatewayRefreshRotateResponse;
import com.example.gateway.security.session.domain.GatewayRefreshTokenFamilyState;
import com.example.gateway.security.session.domain.RefreshTokenFamilyRotateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "gateway.session", name = "enabled", havingValue = "true")
public class GatewayRefreshTokenOpsService {

    private static final String EVENT_REFRESH_REUSE = "REFRESH_REUSE";

    private final GatewayRefreshTokenFamilyRepository tokenFamilyRepository;
    private final GatewayRefreshTokenHasher refreshTokenHasher;
    private final GatewaySessionRevocationService sessionRevocationService;

    public Mono<GatewayRefreshRotateResponse> rotateRefreshTokenFamily(GatewayRefreshRotateRequest request) {
        ValidationResult validation = validateRequest(request);
        if (!validation.isValid()) {
            return Mono.error(new IllegalArgumentException(validation.message()));
        }

        long now = System.currentTimeMillis();
        String currentHash = refreshTokenHasher.hash(request.currentRefreshToken());
        String nextHash = refreshTokenHasher.hash(request.nextRefreshToken());
        String familyId = request.tokenFamilyId();
        Long userId = request.userId();
        String sessionId = request.sessionId();

        return tokenFamilyRepository.findByFamilyId(familyId)
                .flatMap(state -> handleExistingFamily(state, userId, familyId, sessionId, currentHash, nextHash, now))
                .switchIfEmpty(Mono.defer(() -> initializeFamily(userId, familyId, sessionId, nextHash, now)));
    }

    private Mono<GatewayRefreshRotateResponse> handleExistingFamily(GatewayRefreshTokenFamilyState state,
                                                                    Long userId,
                                                                    String familyId,
                                                                    String sessionId,
                                                                    String currentHash,
                                                                    String nextHash,
                                                                    long now) {
        if (!StringUtils.hasText(state.currentRefreshTokenHash())) {
            return initializeFamily(userId, familyId, sessionId, nextHash, now);
        }

        if (!state.currentRefreshTokenHash().equals(currentHash)) {
            return detectReuseAndRevoke(state.userId() != null ? state.userId() : userId, familyId, sessionId, now);
        }

        return tokenFamilyRepository.rotateIfCurrentHashMatches(
                        familyId,
                        currentHash,
                        nextHash,
                        userId,
                        sessionId,
                        now
                )
                .flatMap(rotateResult -> {
                    if (rotateResult == RefreshTokenFamilyRotateResult.ROTATED) {
                        return Mono.just(new GatewayRefreshRotateResponse(
                                userId,
                                familyId,
                                "ROTATED",
                                0L,
                                false,
                                false
                        ));
                    }
                    if (rotateResult == RefreshTokenFamilyRotateResult.FAMILY_NOT_FOUND) {
                        return initializeFamily(userId, familyId, sessionId, nextHash, now);
                    }
                    return detectReuseAndRevoke(userId, familyId, sessionId, now);
                });
    }

    private Mono<GatewayRefreshRotateResponse> initializeFamily(Long userId,
                                                                String familyId,
                                                                String sessionId,
                                                                String nextHash,
                                                                long now) {
        return tokenFamilyRepository.upsert(familyId, userId, sessionId, nextHash, false, now)
                .thenReturn(new GatewayRefreshRotateResponse(
                        userId,
                        familyId,
                        "INITIALIZED",
                        0L,
                        false,
                        false
                ));
    }

    private Mono<GatewayRefreshRotateResponse> detectReuseAndRevoke(Long userId,
                                                                    String familyId,
                                                                    String sessionId,
                                                                    long now) {
        return tokenFamilyRepository.markReuseDetected(familyId, now)
                .onErrorResume(error -> {
                    log.warn("Failed to mark refresh reuse detected. userId={}, familyId={}, sid={}, reason={}",
                            userId, familyId, sessionId, error.toString());
                    return Mono.empty();
                })
                .then(sessionRevocationService.revokeAllByUserId(
                        userId,
                        EVENT_REFRESH_REUSE,
                        sessionId,
                        familyId
                ))
                .map(revocationResult -> new GatewayRefreshRotateResponse(
                        revocationResult.userId(),
                        familyId,
                        "REUSE_DETECTED",
                        revocationResult.revokedCount(),
                        revocationResult.keycloakLogoutAttempted(),
                        revocationResult.keycloakLogoutSucceeded()
                ));
    }

    private ValidationResult validateRequest(GatewayRefreshRotateRequest request) {
        if (request == null) {
            return ValidationResult.invalid("요청 본문이 필요합니다");
        }
        if (request.userId() == null || request.userId() <= 0) {
            return ValidationResult.invalid("userId는 1 이상이어야 합니다");
        }
        if (!StringUtils.hasText(request.tokenFamilyId())) {
            return ValidationResult.invalid("tokenFamilyId가 비어 있습니다");
        }
        if (!StringUtils.hasText(request.currentRefreshToken())) {
            return ValidationResult.invalid("currentRefreshToken이 비어 있습니다");
        }
        if (!StringUtils.hasText(request.nextRefreshToken())) {
            return ValidationResult.invalid("nextRefreshToken이 비어 있습니다");
        }
        return ValidationResult.ok();
    }

    private record ValidationResult(boolean isValid, String message) {
        private static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        private static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
    }
}
