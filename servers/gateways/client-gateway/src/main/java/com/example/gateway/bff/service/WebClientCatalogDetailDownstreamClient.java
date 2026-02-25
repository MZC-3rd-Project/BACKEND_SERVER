package com.example.gateway.bff.service;

import com.example.gateway.bff.dto.BffItemType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
public class WebClientCatalogDetailDownstreamClient implements CatalogDetailDownstreamClient {

    private final WebClient hotDealWebClient;
    private final WebClient fundingWebClient;
    private final WebClient productWebClient;
    private final WebClient mediaWebClient;
    private final ObjectMapper objectMapper;

    public WebClientCatalogDetailDownstreamClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.service.hot-deal-url:http://localhost:8089}") String hotDealServiceUrl,
            @Value("${app.service.funding-url:http://localhost:8086}") String fundingServiceUrl,
            @Value("${app.service.product-url:http://localhost:8084}") String productServiceUrl,
            @Value("${app.service.media-url:http://localhost:8094}") String mediaServiceUrl
    ) {
        this.hotDealWebClient = webClientBuilder.baseUrl(hotDealServiceUrl).build();
        this.fundingWebClient = webClientBuilder.baseUrl(fundingServiceUrl).build();
        this.productWebClient = webClientBuilder.baseUrl(productServiceUrl).build();
        this.mediaWebClient = webClientBuilder.baseUrl(mediaServiceUrl).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<ResponseEntity<JsonNode>> fetchHotDealDetail(Long hotDealId, HttpHeaders headers) {
        return callGet(hotDealWebClient, "/api/v1/hot-deals/" + hotDealId, headers);
    }

    @Override
    public Mono<ResponseEntity<JsonNode>> fetchFundingDetail(Long campaignId, HttpHeaders headers) {
        return callGet(fundingWebClient, "/api/campaigns/" + campaignId, headers);
    }

    @Override
    public Mono<ResponseEntity<JsonNode>> fetchFundingDetailByItem(Long itemId, HttpHeaders headers) {
        return callGet(fundingWebClient, "/api/campaigns/item/" + itemId, headers);
    }

    @Override
    public Mono<ResponseEntity<JsonNode>> fetchNormalDetail(BffItemType itemType, Long itemId, HttpHeaders headers) {
        return callGet(productWebClient, itemType.collectionPath() + "/" + itemId, headers);
    }

    @Override
    public Mono<ResponseEntity<JsonNode>> fetchFundingParticipations(Long campaignId, HttpHeaders headers) {
        return callGet(fundingWebClient, "/api/campaigns/" + campaignId + "/participations", headers);
    }

    @Override
    public Mono<ResponseEntity<JsonNode>> fetchMediaUrls(List<Long> mediaIds, HttpHeaders headers) {
        return mediaWebClient.method(HttpMethod.POST)
                .uri("/internal/v1/media/urls/batch")
                .headers(requestHeaders -> requestHeaders.addAll(headers))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("mediaIds", mediaIds))
                .exchangeToMono(response -> response.bodyToMono(JsonNode.class)
                        .defaultIfEmpty(objectMapper.createObjectNode())
                        .map(payload -> ResponseEntity.status(response.statusCode()).body(payload)));
    }

    private Mono<ResponseEntity<JsonNode>> callGet(WebClient client, String path, HttpHeaders headers) {
        return client.get()
                .uri(path)
                .headers(requestHeaders -> requestHeaders.addAll(headers))
                .exchangeToMono(response -> response.bodyToMono(JsonNode.class)
                        .defaultIfEmpty(objectMapper.createObjectNode())
                        .map(payload -> ResponseEntity.status(response.statusCode()).body(payload)));
    }
}
