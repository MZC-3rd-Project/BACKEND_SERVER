package com.example.gateway.security.session.application;

import com.example.gateway.config.GatewayDevLoginProperties;
import com.example.gateway.config.GatewaySessionProperties;
import com.example.gateway.security.GatewaySessionPrincipal;
import com.example.gateway.security.SessionClaimParser;
import com.example.gateway.security.session.application.port.GatewaySessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class GatewaySessionPrincipalResolver {

    private final SessionClaimParser sessionClaimParser;
    private final GatewaySessionProperties sessionProperties;
    private GatewaySessionRepository sessionRepository;
    private GatewayDevLoginProperties devLoginProperties;

    public GatewaySessionPrincipalResolver(SessionClaimParser sessionClaimParser,
                                           GatewaySessionProperties sessionProperties) {
        this.sessionClaimParser = sessionClaimParser;
        this.sessionProperties = sessionProperties;
    }

    @Autowired(required = false)
    public void setDevLoginProperties(GatewayDevLoginProperties devLoginProperties) {
        this.devLoginProperties = devLoginProperties;
    }

    @Autowired(required = false)
    public void setSessionRepository(GatewaySessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public Mono<GatewaySessionPrincipal> resolve(ServerWebExchange exchange) {
        return resolveFromExchangePrincipal(exchange)
                .switchIfEmpty(resolveFromSecurityContext())
                .switchIfEmpty(resolveFromSessionCookie(exchange));
    }

    public Mono<GatewaySessionPrincipal> resolveFromSecurityContext() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(Authentication::isAuthenticated)
                .flatMap(this::mapAuthenticationToPrincipal);
    }

    private Mono<GatewaySessionPrincipal> resolveFromExchangePrincipal(ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .ofType(Authentication.class)
                .filter(Authentication::isAuthenticated)
                .flatMap(this::mapAuthenticationToPrincipal);
    }

    private Mono<GatewaySessionPrincipal> resolveFromSessionCookie(ServerWebExchange exchange) {
        String cookieName = sessionProperties.getSessionCookieName();
        if (!StringUtils.hasText(cookieName)) {
            return Mono.empty();
        }
        String sessionId = exchange.getRequest().getCookies().getFirst(cookieName) != null
                ? exchange.getRequest().getCookies().getFirst(cookieName).getValue()
                : null;
        if (!StringUtils.hasText(sessionId)) {
            return Mono.empty();
        }
        if (sessionRepository == null) {
            return Mono.empty();
        }
        return sessionRepository.findSessionById(sessionId)
                .filter(session -> session.userId() != null && session.userId() > 0)
                .filter(session -> StringUtils.hasText(session.status())
                        && session.status().equalsIgnoreCase(sessionProperties.getActiveStatus()))
                .flatMap(session -> sessionRepository.touchSession(session.sessionId(), System.currentTimeMillis())
                        .onErrorResume(error -> Mono.empty())
                        .thenReturn(new GatewaySessionPrincipal(
                                session.userId(),
                                session.roles() == null ? List.of() : session.roles(),
                                session.sessionId()
                        )));
    }

    private Mono<GatewaySessionPrincipal> mapAuthenticationToPrincipal(Authentication authentication) {
        Map<String, Object> claims = extractClaims(authentication);
        if (claims.isEmpty()) {
            return Mono.empty();
        }
        return Mono.fromCallable(() -> sessionClaimParser.parseClaims(claims));
    }

    private Map<String, Object> extractClaims(Authentication authentication) {
        Map<String, Object> claims = new LinkedHashMap<>();
        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            claims.putAll(oidcUser.getClaims());
        } else if (principal instanceof OAuth2AuthenticatedPrincipal oauth2Principal) {
            claims.putAll(oauth2Principal.getAttributes());
        } else if (supportsDevLogin(authentication)) {
            claims.put("userId", devLoginProperties.getUserId());
            claims.put("sid", devLoginProperties.getSessionIdPrefix() + "-" + authentication.getName());
        } else {
            return Map.of();
        }

        if (!claims.containsKey("roles")) {
            List<String> roles = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(StringUtils::hasText)
                    .map(this::normalizeRoleAuthority)
                    .collect(Collectors.toList());
            if (!roles.isEmpty()) {
                claims.put("roles", roles);
            }
        }
        return claims;
    }

    private boolean supportsDevLogin(Authentication authentication) {
        return devLoginProperties != null
                && devLoginProperties.isEnabled()
                && authentication != null
                && StringUtils.hasText(authentication.getName())
                && authentication.getName().equals(devLoginProperties.getUsername())
                && devLoginProperties.getUserId() != null
                && devLoginProperties.getUserId() > 0;
    }

    private String normalizeRoleAuthority(String authority) {
        if (authority.startsWith("ROLE_")) {
            return authority.substring("ROLE_".length());
        }
        return authority;
    }
}
