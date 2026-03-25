package com.example.gateway.bff.service;

import com.example.gateway.config.GatewaySecurityProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class FundingBffService {

    private final WebClient.Builder webClientBuilder;
    private final GatewaySecurityProperties securityProperties;

    @Value("${app.service.funding-url:http://localhost:8086}")
    private String fundingServiceUrl;

    public Mono<ResponseEntity<JsonNode>> fetchCampaigns(URI requestUri) {
        return proxyGet(builder -> {
            UriBuilder uriBuilder = builder.path("/api/campaigns");
            if (requestUri.getQuery() != null && !requestUri.getQuery().isBlank()) {
                uriBuilder.query(requestUri.getQuery());
            }
            return uriBuilder.build();
        });
    }

    public Mono<ResponseEntity<JsonNode>> fetchCampaign(Long campaignId) {
        return proxyGet(builder -> builder.path("/api/campaigns/{campaignId}").build(campaignId));
    }

    public Mono<ResponseEntity<JsonNode>> fetchClosingSoon(URI requestUri) {
        return proxyGet(builder -> {
            UriBuilder uriBuilder = builder.path("/api/campaigns/closing-soon");
            if (requestUri.getQuery() != null && !requestUri.getQuery().isBlank()) {
                uriBuilder.query(requestUri.getQuery());
            }
            return uriBuilder.build();
        });
    }

    private Mono<ResponseEntity<JsonNode>> proxyGet(Function<UriBuilder, URI> uriFunction) {
        return webClientBuilder.baseUrl(fundingServiceUrl)
                .build()
                .get()
                .uri(uriFunction)
                .headers(this::applyInternalAuthHeader)
                .exchangeToMono(response -> response.bodyToMono(JsonNode.class)
                        .defaultIfEmpty(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode())
                        .map(body -> ResponseEntity.status(response.statusCode()).body(body)));
    }

    private void applyInternalAuthHeader(HttpHeaders headers) {
        if (StringUtils.hasText(securityProperties.getInternalAuthHeader())
                && StringUtils.hasText(securityProperties.getInternalAuthToken())) {
            headers.set(securityProperties.getInternalAuthHeader(), securityProperties.getInternalAuthToken());
        }
    }
}
