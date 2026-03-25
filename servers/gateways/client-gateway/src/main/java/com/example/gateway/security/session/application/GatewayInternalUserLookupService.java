package com.example.gateway.security.session.application;

import com.example.gateway.config.GatewayAuthProperties;
import com.example.gateway.config.GatewaySecurityProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class GatewayInternalUserLookupService {

    private final WebClient.Builder webClientBuilder;
    private final GatewayAuthProperties authProperties;
    private final GatewaySecurityProperties securityProperties;

    public Mono<Long> findUserIdByKeycloakId(String keycloakId) {
        if (!StringUtils.hasText(keycloakId)) {
            return Mono.empty();
        }
        return webClientBuilder.baseUrl(authProperties.getAuthServiceUrl())
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder.path("/internal/v1/users/by-keycloak/{keycloakId}").build(keycloakId))
                .headers(this::applyInternalAuthHeader)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(JsonNode.class)
                                .flatMap(this::extractUserId);
                    }
                    if (response.statusCode().value() == 404) {
                        return Mono.empty();
                    }
                    return response.createException().flatMap(Mono::error);
                });
    }

    private Mono<Long> extractUserId(JsonNode body) {
        JsonNode node = body.path("data").path("userId");
        if (node.isMissingNode() || node.isNull()) {
            return Mono.empty();
        }
        long userId = node.asLong(-1L);
        return userId > 0 ? Mono.just(userId) : Mono.empty();
    }

    private void applyInternalAuthHeader(HttpHeaders headers) {
        if (StringUtils.hasText(securityProperties.getInternalAuthHeader())
                && StringUtils.hasText(securityProperties.getInternalAuthToken())) {
            headers.set(securityProperties.getInternalAuthHeader(), securityProperties.getInternalAuthToken());
        }
    }
}
