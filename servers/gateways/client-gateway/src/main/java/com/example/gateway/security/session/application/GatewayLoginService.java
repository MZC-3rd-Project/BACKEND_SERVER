package com.example.gateway.security.session.application;

import com.example.gateway.config.GatewayOidcProperties;
import com.example.gateway.config.GatewaySessionProperties;
import com.example.gateway.security.GatewaySessionPrincipal;
import com.example.gateway.security.SessionClaimParseException;
import com.example.gateway.security.SessionClaimParser;
import com.example.gateway.security.session.application.port.GatewaySessionRepository;
import com.example.gateway.security.session.domain.GatewayOidcTokenResponse;
import com.example.gateway.security.session.domain.GatewayServerSession;
import com.example.gateway.security.session.infra.keycloak.GatewayOidcTokenClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GatewayLoginService {

    private final GatewayAuthorizationStateService authorizationStateService;
    private final GatewayOidcTokenClient oidcTokenClient;
    private final GatewayInternalUserLookupService userLookupService;
    private final GatewaySessionRepository sessionRepository;
    private final GatewaySessionTokenCipher sessionTokenCipher;
    private final GatewaySessionCookieManager sessionCookieManager;
    private final GatewayRefreshTokenHasher refreshTokenHasher;
    private final SessionClaimParser sessionClaimParser;
    private final GatewayOidcProperties oidcProperties;
    private final GatewaySessionProperties sessionProperties;
    private final ObjectMapper objectMapper;

    public Mono<String> buildAuthorizationRedirect(String redirectPath) {
        String normalizedRedirect = normalizeRedirectPath(redirectPath);
        String state = UUID.randomUUID().toString();
        return authorizationStateService.save(state, normalizedRedirect)
                .thenReturn(oidcProperties.authorizationEndpoint()
                        + "?response_type=code"
                        + "&client_id=" + urlEncode(oidcProperties.getClientId())
                        + "&scope=" + urlEncode(oidcProperties.getScopes().replace(",", " "))
                        + "&redirect_uri=" + urlEncode(oidcProperties.getRedirectUri())
                        + "&state=" + urlEncode(state));
    }

    public Mono<String> completeLogin(ServerWebExchange exchange, String code, String state) {
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            return Mono.error(new IllegalArgumentException("authorization code 또는 state가 비어 있습니다"));
        }

        return authorizationStateService.consume(state)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("유효하지 않거나 만료된 state입니다")))
                .flatMap(redirectPath -> oidcTokenClient.exchangeAuthorizationCode(code)
                        .flatMap(tokenResponse -> createSession(exchange, tokenResponse).thenReturn(redirectPath)));
    }

    private Mono<Void> createSession(ServerWebExchange exchange, GatewayOidcTokenResponse tokenResponse) {
        Map<String, Object> claims = decodeClaims(firstNonBlank(tokenResponse.idToken(), tokenResponse.accessToken()));
        String keycloakSubject = stringValue(claims.get("sub"));
        String keycloakSessionId = firstNonBlank(stringValue(claims.get("sid")), stringValue(claims.get("session_state")));
        String email = stringValue(claims.get("email"));
        String refreshToken = tokenResponse.refreshToken();
        long now = System.currentTimeMillis();

        return resolveCanonicalUserId(claims, keycloakSubject)
                .flatMap(userId -> {
                    String gatewaySessionId = UUID.randomUUID().toString();
                    Map<String, Object> normalizedClaims = new LinkedHashMap<>(claims);
                    normalizedClaims.put("userId", userId);
                    normalizedClaims.put("sid", gatewaySessionId);
                    GatewaySessionPrincipal principal = sessionClaimParser.parseClaims(normalizedClaims);
                    String tokenFamilyId = UUID.randomUUID().toString();

                    GatewayServerSession session = new GatewayServerSession(
                            gatewaySessionId,
                            principal.userId(),
                            principal.roles(),
                            keycloakSubject,
                            keycloakSessionId,
                            email,
                            sessionTokenCipher.encrypt(refreshToken),
                            refreshTokenHasher.hash(refreshToken),
                            tokenFamilyId,
                            sessionProperties.getActiveStatus(),
                            now,
                            resolveAccessTokenExpiresAt(tokenResponse, now),
                            now,
                            now
                    );

                    return sessionRepository.saveSession(session)
                            .then(Mono.fromRunnable(() -> sessionCookieManager.addSessionCookie(exchange, gatewaySessionId)));
                });
    }

    public Mono<Void> logout(ServerWebExchange exchange) {
        String sessionId = sessionCookieManager.extractSessionId(exchange);
        if (!StringUtils.hasText(sessionId)) {
            sessionCookieManager.expireSessionCookie(exchange);
            return Mono.empty();
        }
        return sessionRepository.revokeSession(sessionId)
                .onErrorResume(error -> Mono.empty())
                .then(Mono.fromRunnable(() -> sessionCookieManager.expireSessionCookie(exchange)));
    }

    private Mono<Long> resolveCanonicalUserId(Map<String, Object> claims, String keycloakSubject) {
        try {
            return Mono.just(sessionClaimParser.parseClaims(claims).userId());
        } catch (SessionClaimParseException ignored) {
            if (!StringUtils.hasText(keycloakSubject)) {
                return Mono.error(new SessionClaimParseException("세션 클레임에서 유효한 사용자 ID를 찾지 못했습니다"));
            }
            return userLookupService.findUserIdByKeycloakId(keycloakSubject)
                    .switchIfEmpty(Mono.error(new SessionClaimParseException("Keycloak 사용자와 내부 userId 매핑을 찾지 못했습니다")));
        }
    }

    private Map<String, Object> decodeClaims(String jwt) {
        if (!StringUtils.hasText(jwt)) {
            throw new IllegalArgumentException("토큰이 비어 있습니다");
        }
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                throw new IllegalArgumentException("JWT 형식이 아닙니다");
            }
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            return objectMapper.readValue(decoded, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("토큰 클레임 파싱에 실패했습니다", e);
        }
    }

    private String normalizeRedirectPath(String redirectPath) {
        if (!StringUtils.hasText(redirectPath) || !redirectPath.startsWith("/")) {
            return oidcProperties.getLoginSuccessUrl();
        }
        if (redirectPath.startsWith("/oauth2/") || redirectPath.startsWith("/login/oauth2/")) {
            return oidcProperties.getLoginSuccessUrl();
        }
        return redirectPath;
    }

    private long resolveAccessTokenExpiresAt(GatewayOidcTokenResponse tokenResponse, long now) {
        if (tokenResponse.expiresIn() <= 0L) {
            return now;
        }
        return now + tokenResponse.expiresIn() * 1000L;
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return StringUtils.hasText(second) ? second : null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String urlEncode(String raw) {
        return java.net.URLEncoder.encode(raw, StandardCharsets.UTF_8);
    }
}
