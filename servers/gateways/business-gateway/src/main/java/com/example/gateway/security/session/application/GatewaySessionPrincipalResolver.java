package com.example.gateway.security.session.application;

import com.example.gateway.config.BusinessGatewaySessionProperties;
import com.example.gateway.security.GatewaySessionPrincipal;
import com.example.gateway.security.session.application.port.GatewaySessionRepository;
import com.example.gateway.security.session.domain.GatewayServerSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class GatewaySessionPrincipalResolver {

    private final GatewaySessionRepository sessionRepository;
    private final GatewaySessionCookieManager sessionCookieManager;
    private final BusinessGatewaySessionProperties sessionProperties;

    public Mono<GatewaySessionPrincipal> resolve(ServerWebExchange exchange) {
        return resolveFromSecurityContext()
                .switchIfEmpty(resolveFromSessionCookie(exchange));
    }

    public Mono<GatewaySessionPrincipal> resolveFromSecurityContext() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(Objects::nonNull)
                .filter(Authentication::isAuthenticated)
                .flatMap(authentication -> {
                    Object principal = authentication.getPrincipal();
                    if (!(principal instanceof GatewaySessionPrincipal sessionPrincipal)) {
                        return Mono.empty();
                    }
                    return Mono.just(sessionPrincipal);
                });
    }

    private Mono<GatewaySessionPrincipal> resolveFromSessionCookie(ServerWebExchange exchange) {
        String sessionId = sessionCookieManager.extractSessionId(exchange);
        if (sessionId == null || sessionId.isBlank()) {
            return Mono.empty();
        }
        return sessionRepository.findSessionById(sessionId)
                .filter(this::isActive)
                .flatMap(session -> sessionRepository.touchSession(session.sessionId(), System.currentTimeMillis())
                        .thenReturn(new GatewaySessionPrincipal(session.userId(), session.roles(), session.sessionId())));
    }

    private boolean isActive(GatewayServerSession session) {
        return session != null
                && session.userId() != null
                && session.userId() > 0
                && sessionProperties.getActiveStatus().equalsIgnoreCase(session.status());
    }
}
