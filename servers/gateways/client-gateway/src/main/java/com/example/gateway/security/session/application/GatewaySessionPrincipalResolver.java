package com.example.gateway.security.session.application;

import com.example.gateway.config.GatewaySessionProperties;
import com.example.gateway.security.GatewaySessionPrincipal;
import com.example.gateway.security.session.application.port.GatewaySessionRepository;
import com.example.gateway.security.session.domain.GatewayServerSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class GatewaySessionPrincipalResolver {

    private final GatewaySessionRepository sessionRepository;
    private final GatewaySessionCookieManager sessionCookieManager;
    private final GatewaySessionProperties sessionProperties;

    public Mono<GatewaySessionPrincipal> resolve(org.springframework.web.server.ServerWebExchange exchange) {
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

    private Mono<GatewaySessionPrincipal> resolveFromSessionCookie(org.springframework.web.server.ServerWebExchange exchange) {
        String sessionId = sessionCookieManager.extractSessionId(exchange);
        if (sessionId == null || sessionId.isBlank()) {
            log.info("Gateway session resolve skipped. path={}, reason=no-session-cookie",
                    exchange.getRequest().getURI().getPath());
            return Mono.empty();
        }
        return sessionRepository.findSessionById(sessionId)
                .filter(this::isActive)
                .doOnNext(session -> log.info("Gateway session resolved from redis. path={}, sid={}, userId={}",
                        exchange.getRequest().getURI().getPath(),
                        session.sessionId(),
                        session.userId()))
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
