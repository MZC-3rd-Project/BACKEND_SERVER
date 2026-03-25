package com.example.gateway.security;

import com.example.gateway.config.GatewayAuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.DefaultServerRedirectStrategy;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.net.URI;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "gateway.auth", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "gateway.session", name = "enabled", havingValue = "true")
public class GatewayAuthFailureRedirectHandler implements ServerAuthenticationFailureHandler {

    private final GatewayAuthProperties authProperties;

    @Override
    public Mono<Void> onAuthenticationFailure(WebFilterExchange webFilterExchange, AuthenticationException exception) {
        return new DefaultServerRedirectStrategy()
                .sendRedirect(webFilterExchange.getExchange(), URI.create(authProperties.getLoginFailureUrl()));
    }
}
