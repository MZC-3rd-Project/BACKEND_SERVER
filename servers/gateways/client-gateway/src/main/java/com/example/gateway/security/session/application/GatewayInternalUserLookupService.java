package com.example.gateway.security.session.application;

import com.example.gateway.config.GatewaySecurityProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class GatewayInternalUserLookupService {

    private final WebClient.Builder webClientBuilder;
    private final GatewaySecurityProperties securityProperties;
    @Value("${app.service.auth-url:http://localhost:8081}")
    private String authServiceUrl;

    public Mono<Long> findUserIdByKeycloakId(String keycloakId) {
        if (!StringUtils.hasText(keycloakId)) {
            return Mono.empty();
        }
        return webClientBuilder.baseUrl(authServiceUrl)
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder.path("/internal/v1/users/by-keycloak/{keycloakId}").build(keycloakId))
                .headers(this::applyInternalAuthHeader)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(body -> {
                    JsonNode userIdNode = body.path("data").path("userId");
                    if (userIdNode.isMissingNode() || userIdNode.isNull()) {
                        return Mono.empty();
                    }
                    long userId = userIdNode.asLong(-1L);
                    return userId > 0 ? Mono.just(userId) : Mono.empty();
                });
    }

    private void applyInternalAuthHeader(HttpHeaders headers) {
        if (StringUtils.hasText(securityProperties.getInternalAuthHeader())
                && StringUtils.hasText(securityProperties.getInternalAuthToken())) {
            headers.set(securityProperties.getInternalAuthHeader(), securityProperties.getInternalAuthToken());
        }
    }
}
