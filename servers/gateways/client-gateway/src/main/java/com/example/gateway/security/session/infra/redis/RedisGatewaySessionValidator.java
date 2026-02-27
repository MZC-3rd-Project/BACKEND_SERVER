package com.example.gateway.security.session.infra.redis;

import com.example.gateway.config.GatewaySessionProperties;
import com.example.gateway.security.GatewaySessionPrincipal;
import com.example.gateway.security.session.application.port.GatewaySessionRepository;
import com.example.gateway.security.session.application.port.GatewaySessionValidator;
import com.example.gateway.security.session.domain.SessionValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "gateway.session", name = "enabled", havingValue = "true")
public class RedisGatewaySessionValidator implements GatewaySessionValidator {

    private static final String CODE_SESSION_ID_MISSING = "GW-AUTH-005";
    private static final String CODE_SESSION_NOT_ACTIVE = "GW-AUTH-006";
    private static final String CODE_SESSION_VALIDATION_FAILED = "GW-AUTH-007";

    private final GatewaySessionRepository sessionRepository;
    private final GatewaySessionProperties sessionProperties;

    @Override
    public Mono<SessionValidationResult> validate(GatewaySessionPrincipal principal) {
        if (principal == null) {
            return Mono.just(SessionValidationResult.allow());
        }

        String sessionId = principal.sessionId();
        if (!StringUtils.hasText(sessionId)) {
            return Mono.just(SessionValidationResult.deny(
                    CODE_SESSION_ID_MISSING,
                    "세션 클레임에서 세션 식별자(sid)를 찾지 못했습니다"
            ));
        }

        return sessionRepository.findStatusBySid(sessionId)
                .map(String::valueOf)
                .flatMap(status -> handleStatus(status, principal.userId(), sessionId))
                .switchIfEmpty(Mono.defer(() -> activateAndAllow(principal.userId(), sessionId)))
                .onErrorResume(error -> {
                    log.warn("Gateway session validation failed. sid={}, reason={}",
                            sessionId, error.toString());
                    return Mono.just(SessionValidationResult.deny(
                            CODE_SESSION_VALIDATION_FAILED,
                            "세션 상태 검증에 실패했습니다"
                    ));
                });
    }

    private Mono<SessionValidationResult> handleStatus(String status, Long userId, String sessionId) {
        if (isActive(status)) {
            return indexSession(userId, sessionId)
                    .thenReturn(SessionValidationResult.allow());
        }
        return Mono.just(SessionValidationResult.deny(
                CODE_SESSION_NOT_ACTIVE,
                "세션이 존재하지 않거나 비활성화 상태입니다"
        ));
    }

    private boolean isActive(String status) {
        if (StringUtils.hasText(status) && status.equalsIgnoreCase(sessionProperties.getActiveStatus())) {
            return true;
        }
        return false;
    }

    private Mono<SessionValidationResult> activateAndAllow(Long userId, String sessionId) {
        return sessionRepository.activateSession(userId, sessionId)
                .onErrorResume(error -> {
                    log.warn("Gateway session activation failed. sid={}, userId={}, reason={}",
                            sessionId, userId, error.toString());
                    return Mono.error(error);
                })
                .thenReturn(SessionValidationResult.allow());
    }

    private Mono<Void> indexSession(Long userId, String sessionId) {
        return sessionRepository.indexUserSession(userId, sessionId)
                .onErrorResume(error -> {
                    log.warn("Gateway session indexing failed. sid={}, userId={}, reason={}",
                            sessionId, userId, error.toString());
                    return Mono.empty();
                });
    }
}
