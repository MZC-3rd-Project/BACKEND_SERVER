package com.example.gateway.security.session.infra.keycloak;

import com.example.gateway.config.BusinessGatewayOidcProperties;
import com.example.gateway.security.session.domain.GatewayOidcTokenResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class GatewayOidcTokenClient {

    private final WebClient.Builder webClientBuilder;
    private final BusinessGatewayOidcProperties oidcProperties;

    public Mono<GatewayOidcTokenResponse> exchangeAuthorizationCode(String code) {
        if (!StringUtils.hasText(code)) {
            return Mono.error(new IllegalArgumentException("authorization code가 비어 있습니다"));
        }
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", oidcProperties.getRedirectUri());
        form.add("client_id", oidcProperties.getClientId());
        return requestToken(form);
    }

    public Mono<GatewayOidcTokenResponse> refresh(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return Mono.error(new IllegalArgumentException("refresh token이 비어 있습니다"));
        }
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id", oidcProperties.getClientId());
        return requestToken(form);
    }

    private Mono<GatewayOidcTokenResponse> requestToken(LinkedMultiValueMap<String, String> form) {
        return webClientBuilder.build()
                .post()
                .uri(oidcProperties.tokenEndpoint())
                .headers(headers -> applyClientAuthentication(headers, form))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(body -> new GatewayOidcTokenResponse(
                        body.path("access_token").asText(null),
                        body.path("refresh_token").asText(null),
                        body.path("id_token").asText(null),
                        body.path("expires_in").asLong(0L)
                ));
    }

    private void applyClientAuthentication(HttpHeaders headers, LinkedMultiValueMap<String, String> form) {
        if ("client_secret_post".equalsIgnoreCase(oidcProperties.getClientAuthenticationMethod())) {
            form.add("client_secret", oidcProperties.getClientSecret());
            return;
        }
        String raw = oidcProperties.getClientId() + ":" + oidcProperties.getClientSecret();
        headers.set(HttpHeaders.AUTHORIZATION,
                "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
    }
}
