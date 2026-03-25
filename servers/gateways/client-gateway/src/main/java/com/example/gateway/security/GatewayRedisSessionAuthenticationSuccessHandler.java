package com.example.gateway.security;

import com.example.gateway.config.GatewayAuthProperties;
import com.example.gateway.security.session.application.GatewayRedisSessionLoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.web.server.DefaultServerRedirectStrategy;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "gateway.auth", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "gateway.session", name = "enabled", havingValue = "true")
@ConditionalOnBean(ServerOAuth2AuthorizedClientRepository.class)
public class GatewayRedisSessionAuthenticationSuccessHandler implements ServerAuthenticationSuccessHandler {

    private static final String REGISTRATION_ID = "keycloak";

    private final ServerOAuth2AuthorizedClientRepository authorizedClientRepository;
    private final GatewayRedisSessionLoginService redisSessionLoginService;
    private final GatewayAuthProperties authProperties;

    @Override
    public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange, Authentication authentication) {
        ServerWebExchange exchange = webFilterExchange.getExchange();
        return authorizedClientRepository.loadAuthorizedClient(REGISTRATION_ID, authentication, exchange)
                .switchIfEmpty(Mono.error(new IllegalStateException("OIDC authorized client를 찾지 못했습니다")))
                .flatMap(authorizedClient -> createSessionAndRedirect(exchange, authentication, authorizedClient));
    }

    private Mono<Void> createSessionAndRedirect(ServerWebExchange exchange,
                                                Authentication authentication,
                                                OAuth2AuthorizedClient authorizedClient) {
        return redisSessionLoginService.completeLogin(exchange, authentication, authorizedClient)
                .then(new DefaultServerRedirectStrategy()
                        .sendRedirect(exchange, URI.create(authProperties.getLoginSuccessUrl())));
    }
}
