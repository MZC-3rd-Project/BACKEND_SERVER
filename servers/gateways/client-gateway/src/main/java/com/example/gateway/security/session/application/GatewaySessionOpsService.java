package com.example.gateway.security.session.application;

import com.example.gateway.security.session.application.port.GatewaySessionRepository;
import com.example.gateway.security.session.domain.GatewaySessionListResponse;
import com.example.gateway.security.session.domain.GatewaySessionRevokeResponse;
import com.example.gateway.security.session.domain.GatewaySessionView;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Comparator;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "gateway.session", name = {"enabled", "ops-enabled"}, havingValue = "true")
public class GatewaySessionOpsService {

    private static final String EVENT_MANUAL_REVOKE = "MANUAL_REVOKE";

    private final GatewaySessionRepository sessionRepository;
    private final GatewaySessionRevocationService sessionRevocationService;

    public Mono<GatewaySessionListResponse> findSessionsByUserId(Long userId) {
        if (!isValidUserId(userId)) {
            return Mono.error(new IllegalArgumentException("userId는 1 이상이어야 합니다"));
        }
        return sessionRepository.findSessionsByUserId(userId)
                .sort(Comparator.comparing(GatewaySessionView::sessionId))
                .collectList()
                .map(sessions -> new GatewaySessionListResponse(userId, sessions.size(), sessions));
    }

    public Mono<GatewaySessionRevokeResponse> revokeAllSessionsByUserId(Long userId) {
        if (!isValidUserId(userId)) {
            return Mono.error(new IllegalArgumentException("userId는 1 이상이어야 합니다"));
        }
        return sessionRevocationService.revokeAllByUserId(userId, EVENT_MANUAL_REVOKE, null, null)
                .map(result -> new GatewaySessionRevokeResponse(
                        userId,
                        result.revokedCount(),
                        result.keycloakLogoutAttempted(),
                        result.keycloakLogoutSucceeded()
                ));
    }

    private boolean isValidUserId(Long userId) {
        return userId != null && userId > 0;
    }
}
