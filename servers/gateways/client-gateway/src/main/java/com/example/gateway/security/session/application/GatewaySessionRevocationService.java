package com.example.gateway.security.session.application;

import com.example.gateway.security.session.application.port.GatewayKeycloakSessionClient;
import com.example.gateway.security.session.application.port.GatewaySessionRepository;
import com.example.gateway.security.session.domain.GatewaySessionRevocationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "gateway.session", name = "enabled", havingValue = "true")
public class GatewaySessionRevocationService {

    private final GatewaySessionRepository sessionRepository;
    private final Optional<GatewayKeycloakSessionClient> keycloakSessionClient;

    public Mono<GatewaySessionRevocationResult> revokeAllByUserId(Long userId,
                                                                   String eventType,
                                                                   String sessionId,
                                                                   String tokenFamilyId) {
        if (userId == null || userId <= 0) {
            return Mono.error(new IllegalArgumentException("userId는 1 이상이어야 합니다"));
        }

        return sessionRepository.revokeAllByUserId(userId)
                .flatMap(revokedCount -> {
                    if (keycloakSessionClient.isEmpty()) {
                        log.info("Gateway session revoke completed. eventType={}, userId={}, sid={}, tokenFamilyId={}, revokedCount={}, keycloakLogoutAttempted=false, keycloakLogoutSucceeded=false",
                                eventType, userId, sessionId, tokenFamilyId, revokedCount);
                        return Mono.just(new GatewaySessionRevocationResult(userId, revokedCount, false, false));
                    }

                    return keycloakSessionClient.get().logoutUserSessions(userId)
                            .onErrorResume(error -> {
                                log.warn("Gateway keycloak logout call failed. eventType={}, userId={}, sid={}, tokenFamilyId={}, reason={}",
                                        eventType, userId, sessionId, tokenFamilyId, error.toString());
                                return Mono.just(false);
                            })
                            .map(logoutSucceeded -> {
                                log.info("Gateway session revoke completed. eventType={}, userId={}, sid={}, tokenFamilyId={}, revokedCount={}, keycloakLogoutAttempted=true, keycloakLogoutSucceeded={}",
                                        eventType, userId, sessionId, tokenFamilyId, revokedCount, logoutSucceeded);
                                return new GatewaySessionRevocationResult(userId, revokedCount, true, logoutSucceeded);
                            });
                });
    }
}
