package com.example.gateway.security.session.application;

import com.example.gateway.security.GatewaySessionPrincipal;
import com.example.gateway.security.SessionClaimParser;
import lombok.RequiredArgsConstructor;
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
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GatewaySessionPrincipalResolver {

    private final SessionClaimParser sessionClaimParser;

    public Mono<GatewaySessionPrincipal> resolve(ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .ofType(Authentication.class)
                .filter(Authentication::isAuthenticated)
                .flatMap(this::mapAuthenticationToPrincipal);
    }

    public Mono<GatewaySessionPrincipal> resolveFromSecurityContext() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(Objects::nonNull)
                .filter(Authentication::isAuthenticated)
                .flatMap(this::mapAuthenticationToPrincipal);
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

    private String normalizeRoleAuthority(String authority) {
        if (authority.startsWith("ROLE_")) {
            return authority.substring("ROLE_".length());
        }
        return authority;
    }
}
