package com.example.gateway.security.session.application;

import com.example.gateway.security.GatewaySessionPrincipal;
import com.example.gateway.security.SessionClaimParseException;
import com.example.gateway.security.SessionClaimParser;
import com.example.gateway.security.session.application.port.GatewayRefreshTokenFamilyRepository;
import com.example.gateway.security.session.application.port.GatewaySessionRepository;
import com.example.gateway.security.session.domain.GatewayServerSession;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "gateway.session", name = "enabled", havingValue = "true")
public class GatewayRedisSessionLoginService {

    private final SessionClaimParser sessionClaimParser;
    private final GatewaySessionRepository sessionRepository;
    private final GatewayRefreshTokenFamilyRepository refreshTokenFamilyRepository;
    private final GatewayRefreshTokenHasher refreshTokenHasher;
    private final GatewaySessionTokenCipher sessionTokenCipher;
    private final GatewaySessionCookieManager sessionCookieManager;
    private final GatewayInternalUserLookupService userLookupService;

    public Mono<Void> completeLogin(ServerWebExchange exchange, Authentication authentication, OAuth2AuthorizedClient authorizedClient) {
        Map<String, Object> claims = extractClaims(authentication);
        String gatewaySessionId = UUID.randomUUID().toString();
        String keycloakSubject = stringValue(claims.get("sub"));
        String keycloakSessionId = firstNonBlank(stringValue(claims.get("sid")), stringValue(claims.get("session_state")));
        String email = stringValue(claims.get("email"));
        long now = System.currentTimeMillis();

        return resolveCanonicalUserId(claims, keycloakSubject)
                .flatMap(userId -> {
                    Map<String, Object> normalizedClaims = new LinkedHashMap<>(claims);
                    normalizedClaims.put("userId", userId);
                    normalizedClaims.put("sid", gatewaySessionId);
                    GatewaySessionPrincipal principal = sessionClaimParser.parseClaims(normalizedClaims);

                    String refreshToken = authorizedClient.getRefreshToken() != null
                            ? authorizedClient.getRefreshToken().getTokenValue()
                            : null;
                    if (!StringUtils.hasText(refreshToken)) {
                        return Mono.error(new IllegalStateException("Keycloak refresh token이 비어 있습니다"));
                    }

                    String tokenFamilyId = UUID.randomUUID().toString();
                    String encryptedRefreshToken = sessionTokenCipher.encrypt(refreshToken);
                    String refreshTokenHash = refreshTokenHasher.hash(refreshToken);
                    long accessTokenExpiresAt = resolveAccessTokenExpiry(authorizedClient, now);

                    GatewayServerSession session = new GatewayServerSession(
                            gatewaySessionId,
                            principal.userId(),
                            principal.roles(),
                            keycloakSubject,
                            keycloakSessionId,
                            email,
                            encryptedRefreshToken,
                            refreshTokenHash,
                            tokenFamilyId,
                            "ACTIVE",
                            now,
                            accessTokenExpiresAt,
                            now,
                            now
                    );

                    return sessionRepository.saveSession(session)
                            .then(refreshTokenFamilyRepository.upsert(
                                    tokenFamilyId,
                                    principal.userId(),
                                    gatewaySessionId,
                                    refreshTokenHash,
                                    false,
                                    now
                            ))
                            .then(invalidateWebSession(exchange))
                            .then(Mono.fromRunnable(() -> sessionCookieManager.addSessionCookie(exchange, gatewaySessionId)));
                });
    }

    private Mono<Long> resolveCanonicalUserId(Map<String, Object> claims, String keycloakSubject) {
        try {
            GatewaySessionPrincipal principal = sessionClaimParser.parseClaims(claims);
            return Mono.just(principal.userId());
        } catch (SessionClaimParseException ignored) {
            if (!StringUtils.hasText(keycloakSubject)) {
                return Mono.error(new SessionClaimParseException("세션 클레임에서 유효한 사용자 ID를 찾지 못했습니다"));
            }
            return userLookupService.findUserIdByKeycloakId(keycloakSubject)
                    .switchIfEmpty(Mono.error(new SessionClaimParseException("Keycloak 사용자와 내부 userId 매핑을 찾지 못했습니다")));
        }
    }

    private Mono<Void> invalidateWebSession(ServerWebExchange exchange) {
        return exchange.getSession()
                .flatMap(webSession -> webSession.invalidate().onErrorResume(error -> Mono.empty()))
                .then();
    }

    private long resolveAccessTokenExpiry(OAuth2AuthorizedClient authorizedClient, long fallback) {
        Instant expiresAt = authorizedClient.getAccessToken() != null
                ? authorizedClient.getAccessToken().getExpiresAt()
                : null;
        return expiresAt != null ? expiresAt.toEpochMilli() : fallback;
    }

    private Map<String, Object> extractClaims(Authentication authentication) {
        Map<String, Object> claims = new LinkedHashMap<>();
        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            claims.putAll(oidcUser.getClaims());
        } else if (principal instanceof OAuth2AuthenticatedPrincipal oauth2Principal) {
            claims.putAll(oauth2Principal.getAttributes());
        }

        if (!claims.containsKey("roles")) {
            List<String> roles = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(StringUtils::hasText)
                    .map(role -> role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role)
                    .collect(Collectors.toList());
            if (!roles.isEmpty()) {
                claims.put("roles", roles);
            }
        }
        return claims;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return StringUtils.hasText(second) ? second : null;
    }
}
