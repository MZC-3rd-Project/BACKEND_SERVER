package com.example.gateway.security;

import com.example.gateway.security.session.application.GatewaySessionPrincipalResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class GatewaySessionCookieAuthenticationWebFilter implements WebFilter {

    private final GatewaySessionPrincipalResolver sessionPrincipalResolver;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return sessionPrincipalResolver.resolve(exchange)
                .flatMap(principal -> chain.filter(exchange.mutate().principal(Mono.just(principal)).build())
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                new GatewaySessionAuthentication(principal))))
                .switchIfEmpty(Mono.defer(() -> chain.filter(exchange)));
    }
}
