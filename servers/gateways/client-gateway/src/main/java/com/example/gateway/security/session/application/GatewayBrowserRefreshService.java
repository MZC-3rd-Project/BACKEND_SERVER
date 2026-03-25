package com.example.gateway.security.session.application;

import com.example.gateway.security.session.application.port.GatewaySessionRepository;
import com.example.gateway.security.session.domain.GatewayOidcTokenResponse;
import com.example.gateway.security.session.domain.GatewayRefreshRotateRequest;
import com.example.gateway.security.session.domain.GatewayRefreshRotateResponse;
import com.example.gateway.security.session.domain.GatewayServerSession;
import com.example.gateway.security.session.infra.keycloak.GatewayOidcTokenClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "gateway.session", name = "enabled", havingValue = "true")
public class GatewayBrowserRefreshService {

    private final GatewaySessionRepository sessionRepository;
    private final GatewaySessionCookieManager sessionCookieManager;
    private final GatewaySessionTokenCipher sessionTokenCipher;
    private final GatewayRefreshTokenHasher refreshTokenHasher;
    private final GatewayRefreshTokenOpsService refreshTokenOpsService;
    private final GatewayOidcTokenClient oidcTokenClient;

    public Mono<GatewayRefreshRotateResponse> refresh(ServerWebExchange exchange) {
        String sessionId = sessionCookieManager.extractSessionId(exchange);
        if (!StringUtils.hasText(sessionId)) {
            return Mono.error(new GatewaySessionUnauthorizedException("세션 쿠키를 찾지 못했습니다"));
        }
        return sessionRepository.findSessionById(sessionId)
                .switchIfEmpty(Mono.error(new GatewaySessionUnauthorizedException("세션을 찾지 못했습니다")))
                .flatMap(session -> refreshSession(exchange, session));
    }

    private Mono<GatewayRefreshRotateResponse> refreshSession(ServerWebExchange exchange, GatewayServerSession session) {
        if (!"ACTIVE".equalsIgnoreCase(session.status())) {
            return Mono.error(new GatewaySessionUnauthorizedException("세션이 비활성화 상태입니다"));
        }
        if (!StringUtils.hasText(session.refreshTokenEncrypted())) {
            return Mono.error(new GatewaySessionUnauthorizedException("세션에 refresh token이 없습니다"));
        }

        String currentRefreshToken = sessionTokenCipher.decrypt(session.refreshTokenEncrypted());

        return oidcTokenClient.refresh(currentRefreshToken)
                .flatMap(tokenResponse -> rotateAndPersist(exchange, session, currentRefreshToken, tokenResponse));
    }

    private Mono<GatewayRefreshRotateResponse> rotateAndPersist(ServerWebExchange exchange,
                                                                GatewayServerSession session,
                                                                String currentRefreshToken,
                                                                GatewayOidcTokenResponse tokenResponse) {
        String nextRefreshToken = StringUtils.hasText(tokenResponse.refreshToken())
                ? tokenResponse.refreshToken()
                : currentRefreshToken;
        String tokenFamilyId = StringUtils.hasText(session.tokenFamilyId())
                ? session.tokenFamilyId()
                : UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        GatewayRefreshRotateRequest request = new GatewayRefreshRotateRequest(
                session.userId(),
                tokenFamilyId,
                session.sessionId(),
                currentRefreshToken,
                nextRefreshToken
        );

        return refreshTokenOpsService.rotateRefreshTokenFamily(request)
                .flatMap(response -> {
                    if ("REUSE_DETECTED".equalsIgnoreCase(response.status())) {
                        sessionCookieManager.expireSessionCookie(exchange);
                        return Mono.just(response);
                    }
                    return sessionRepository.updateSessionTokens(
                                    session.sessionId(),
                                    sessionTokenCipher.encrypt(nextRefreshToken),
                                    refreshTokenHasher.hash(nextRefreshToken),
                                    tokenFamilyId,
                                    resolveAccessTokenExpiresAt(tokenResponse, now),
                                    now,
                                    now
                            )
                            .thenReturn(response);
                });
    }

    private long resolveAccessTokenExpiresAt(GatewayOidcTokenResponse response, long now) {
        if (response.expiresIn() <= 0) {
            return now;
        }
        return now + (response.expiresIn() * 1000L);
    }
}
