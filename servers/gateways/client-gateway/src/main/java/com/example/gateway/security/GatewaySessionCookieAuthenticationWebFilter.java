package com.example.gateway.security;

import com.example.gateway.config.GatewaySessionProperties;
import com.example.gateway.security.session.application.GatewaySessionCookieManager;
import com.example.gateway.security.session.application.port.GatewaySessionRepository;
import com.example.gateway.security.session.domain.GatewayServerSession;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "gateway.session", name = "enabled", havingValue = "true")
public class GatewaySessionCookieAuthenticationWebFilter implements WebFilter {

    private final GatewaySessionRepository sessionRepository;
    private final GatewaySessionCookieManager sessionCookieManager;
    private final GatewaySessionProperties sessionProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return exchange.getPrincipal()
                .filter(principal -> principal instanceof Authentication)
                .flatMap(principal -> chain.filter(exchange))
                .switchIfEmpty(Mono.defer(() -> authenticateFromSessionCookie(exchange, chain)));
    }

    private Mono<Void> authenticateFromSessionCookie(ServerWebExchange exchange, WebFilterChain chain) {
        String sessionId = sessionCookieManager.extractSessionId(exchange);
        if (!StringUtils.hasText(sessionId)) {
            return chain.filter(exchange);
        }
        return sessionRepository.findSessionById(sessionId)
                .filter(this::isActiveSession)
                .flatMap(session -> {
                    Authentication authentication = toAuthentication(session);
                    ServerWebExchange authenticatedExchange = exchange.mutate()
                            .principal(Mono.just(authentication))
                            .build();
                    return chain.filter(authenticatedExchange)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    private boolean isActiveSession(GatewayServerSession session) {
        return session != null
                && session.userId() != null
                && session.userId() > 0
                && StringUtils.hasText(session.status())
                && session.status().equalsIgnoreCase(sessionProperties.getActiveStatus());
    }

    private Authentication toAuthentication(GatewayServerSession session) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("userId", session.userId());
        claims.put("sid", session.sessionId());
        if (StringUtils.hasText(session.email())) {
            claims.put("email", session.email());
        }
        if (StringUtils.hasText(session.keycloakSubject())) {
            claims.put("sub", session.keycloakSubject());
        }
        List<String> roles = session.roles() == null ? List.of() : session.roles();
        if (!roles.isEmpty()) {
            claims.put("roles", roles);
        }
        List<GrantedAuthority> authorities = roles.stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role))
                .toList();
        DefaultOAuth2AuthenticatedPrincipal principal =
                new DefaultOAuth2AuthenticatedPrincipal(claims, authorities);
        return new UsernamePasswordAuthenticationToken(principal, "N/A", authorities);
    }
}
