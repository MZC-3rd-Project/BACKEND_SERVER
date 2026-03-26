package com.example.gateway.security.session.application.port;

import com.example.gateway.security.session.domain.GatewayServerSession;
import reactor.core.publisher.Mono;

public interface GatewaySessionRepository {

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
}
