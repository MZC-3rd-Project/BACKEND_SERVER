package com.example.gateway.security.session.infra.keycloak;

import com.example.gateway.config.GatewaySessionProperties;
import com.example.gateway.security.session.application.port.GatewayKeycloakSessionClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "gateway.session", name = {"enabled", "keycloak-logout-enabled"}, havingValue = "true")
public class WebClientGatewayKeycloakSessionClient implements GatewayKeycloakSessionClient {

    private final WebClient.Builder webClientBuilder;
    private final GatewaySessionProperties properties;

    @Override
    public Mono<Boolean> logoutUserSessions(Long userId) {
        if (userId == null || userId <= 0 || !StringUtils.hasText(properties.getKeycloakLogoutUrl())) {
            return Mono.just(false);
        }
        return webClientBuilder.build()
                .post()
                .uri(properties.getKeycloakLogoutUrl())
                .headers(headers -> {
                    if (StringUtils.hasText(properties.getKeycloakLogoutAuthHeader())
                            && StringUtils.hasText(properties.getKeycloakLogoutAuthToken())) {
                        headers.set(properties.getKeycloakLogoutAuthHeader(), properties.getKeycloakLogoutAuthToken());
                    }
                })
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("userId", userId))
                .exchangeToMono(response -> Mono.just(response.statusCode().is2xxSuccessful()))
                .doOnNext(success -> {
                    if (!success) {
                        log.warn("Gateway keycloak logout response was not successful. userId={}, url={}",
                                userId, properties.getKeycloakLogoutUrl());
                    }
                });
    }
}
