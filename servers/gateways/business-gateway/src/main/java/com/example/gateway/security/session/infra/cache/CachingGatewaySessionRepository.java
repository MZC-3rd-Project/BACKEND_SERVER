package com.example.gateway.security.session.infra.cache;

import com.example.gateway.config.BusinessGatewaySessionProperties;
import com.example.gateway.security.session.application.port.GatewaySessionRepository;
import com.example.gateway.security.session.domain.GatewayServerSession;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Primary
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "gateway.session", name = "cache-enabled", havingValue = "true", matchIfMissing = true)
public class CachingGatewaySessionRepository implements GatewaySessionRepository {

    private final @Qualifier("redisGatewaySessionRepository") GatewaySessionRepository delegate;
    private final Cache<String, GatewayServerSession> sessionCache;
    private final BusinessGatewaySessionProperties sessionProperties;

    @Override
    public Mono<Void> saveSession(GatewayServerSession session) {
        return delegate.saveSession(session)
                .doOnSuccess(ignored -> cache(session));
    }

    @Override
    public Mono<GatewayServerSession> findSessionById(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Mono.empty();
        }
        GatewayServerSession cached = sessionCache.getIfPresent(sessionId);
        if (cached != null) {
            return Mono.just(cached);
        }
        return delegate.findSessionById(sessionId)
                .doOnNext(this::cache);
    }

    @Override
    public Mono<Void> updateSessionTokens(String sessionId,
                                          String refreshTokenEncrypted,
                                          String refreshTokenHash,
                                          String tokenFamilyId,
                                          long accessTokenExpiresAtEpochMillis,
                                          long refreshRotatedAtEpochMillis,
                                          long lastSeenAtEpochMillis) {
        return delegate.updateSessionTokens(
                        sessionId,
                        refreshTokenEncrypted,
                        refreshTokenHash,
                        tokenFamilyId,
                        accessTokenExpiresAtEpochMillis,
                        refreshRotatedAtEpochMillis,
                        lastSeenAtEpochMillis
                )
                .doOnSuccess(ignored -> updateCachedSession(sessionId, session -> new GatewayServerSession(
                        session.sessionId(),
                        session.userId(),
                        session.roles(),
                        session.keycloakSubject(),
                        session.keycloakSessionId(),
                        session.email(),
                        refreshTokenEncrypted,
                        refreshTokenHash,
                        tokenFamilyId,
                        session.status(),
                        session.issuedAtEpochMillis(),
                        accessTokenExpiresAtEpochMillis,
                        refreshRotatedAtEpochMillis,
                        lastSeenAtEpochMillis
                )));
    }

    @Override
    public Mono<Void> touchSession(String sessionId, long lastSeenAtEpochMillis) {
        return delegate.touchSession(sessionId, lastSeenAtEpochMillis)
                .doOnSuccess(ignored -> updateCachedSession(sessionId, session -> new GatewayServerSession(
                        session.sessionId(),
                        session.userId(),
                        session.roles(),
                        session.keycloakSubject(),
                        session.keycloakSessionId(),
                        session.email(),
                        session.refreshTokenEncrypted(),
                        session.refreshTokenHash(),
                        session.tokenFamilyId(),
                        session.status(),
                        session.issuedAtEpochMillis(),
                        session.accessTokenExpiresAtEpochMillis(),
                        session.refreshRotatedAtEpochMillis(),
                        lastSeenAtEpochMillis
                )));
    }

    @Override
    public Mono<Void> revokeSession(String sessionId) {
        return delegate.revokeSession(sessionId)
                .doOnSuccess(ignored -> updateCachedSession(sessionId, session -> new GatewayServerSession(
                        session.sessionId(),
                        session.userId(),
                        session.roles(),
                        session.keycloakSubject(),
                        session.keycloakSessionId(),
                        session.email(),
                        session.refreshTokenEncrypted(),
                        session.refreshTokenHash(),
                        session.tokenFamilyId(),
                        sessionProperties.getRevokedStatus(),
                        session.issuedAtEpochMillis(),
                        session.accessTokenExpiresAtEpochMillis(),
                        session.refreshRotatedAtEpochMillis(),
                        session.lastSeenAtEpochMillis()
                )));
    }

    private void cache(GatewayServerSession session) {
        if (session == null || !StringUtils.hasText(session.sessionId())) {
            return;
        }
        sessionCache.put(session.sessionId(), session);
    }

    private void updateCachedSession(String sessionId, java.util.function.Function<GatewayServerSession, GatewayServerSession> updater) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        GatewayServerSession cached = sessionCache.getIfPresent(sessionId);
        if (cached == null) {
            return;
        }
        sessionCache.put(sessionId, updater.apply(cached));
    }
}
