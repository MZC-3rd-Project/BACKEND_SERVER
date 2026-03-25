package com.example.gateway.security.session.application.port;

import com.example.gateway.security.session.domain.GatewaySessionView;
import com.example.gateway.security.session.domain.GatewayServerSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface GatewaySessionRepository {

    Mono<String> findStatusBySid(String sessionId);

    Mono<Void> activateSession(Long userId, String sessionId);

    Mono<Void> indexUserSession(Long userId, String sessionId);

    Mono<Void> saveSession(GatewayServerSession session);

    Mono<GatewayServerSession> findSessionById(String sessionId);

    Mono<Void> updateSessionTokens(String sessionId,
                                   String refreshTokenEncrypted,
                                   String refreshTokenHash,
                                   String tokenFamilyId,
                                   long accessTokenExpiresAtEpochMillis,
                                   long refreshRotatedAtEpochMillis,
                                   long lastSeenAtEpochMillis);

    Mono<Void> touchSession(String sessionId, long lastSeenAtEpochMillis);

    Mono<Void> revokeSession(String sessionId);

    Flux<GatewaySessionView> findSessionsByUserId(Long userId);

    Mono<Long> revokeAllByUserId(Long userId);
}
