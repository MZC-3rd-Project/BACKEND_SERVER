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
    private final WebClient stockWebClient;
    private final WebClient storeQueryWebClient;
    private final WebClient mediaWebClient;
    private final WebClient reviewWebClient;
    private final ObjectMapper objectMapper;

    public WebClientCatalogDetailDownstreamClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.service.hot-deal-url:http://localhost:8089}") String hotDealServiceUrl,
            @Value("${app.service.funding-url:http://localhost:8086}") String fundingServiceUrl,
            @Value("${app.service.product-url:http://localhost:8084}") String productServiceUrl,
            @Value("${app.service.stock-url:http://localhost:8085}") String stockServiceUrl,
            @Value("${app.service.store-query-url:http://localhost:8091}") String storeQueryServiceUrl,
            @Value("${app.service.media-url:http://localhost:8094}") String mediaServiceUrl,
            @Value("${app.service.review-url:http://localhost:8097}") String reviewServiceUrl
    ) {
        this.hotDealWebClient = webClientBuilder.baseUrl(hotDealServiceUrl).build();
        this.fundingWebClient = webClientBuilder.baseUrl(fundingServiceUrl).build();
        this.productWebClient = webClientBuilder.baseUrl(productServiceUrl).build();
        this.stockWebClient = webClientBuilder.baseUrl(stockServiceUrl).build();
        this.storeQueryWebClient = webClientBuilder.baseUrl(storeQueryServiceUrl).build();
        this.mediaWebClient = webClientBuilder.baseUrl(mediaServiceUrl).build();
        this.reviewWebClient = webClientBuilder.baseUrl(reviewServiceUrl).build();
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
    public Mono<ResponseEntity<JsonNode>> fetchItemSummary(Long itemId, HttpHeaders headers) {
        return callGet(productWebClient, "/internal/v1/items/" + itemId, headers);
    }

    @Override
    public Mono<ResponseEntity<JsonNode>> fetchFundingParticipations(Long campaignId, HttpHeaders headers) {
        return callGet(fundingWebClient, "/api/campaigns/" + campaignId + "/participations", headers);
    }

    @Override
    public Mono<ResponseEntity<JsonNode>> fetchStockSummary(Long itemId, HttpHeaders headers) {
        return callGet(stockWebClient, "/internal/v1/stock/items/" + itemId, headers);
    }

    @Override
    public Mono<ResponseEntity<JsonNode>> fetchStoreDetail(Long storeId, HttpHeaders headers) {
        return callGet(storeQueryWebClient, "/api/v1/store-query/stores/" + storeId, headers);
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

    @Override
    public Mono<ResponseEntity<JsonNode>> fetchReviews(Long itemId, HttpHeaders headers) {
        return reviewWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/reviews/items/{itemId}")
                        .queryParam("page", 0)
                        .queryParam("size", 10)
                        .build(itemId))
                .headers(requestHeaders -> requestHeaders.addAll(headers))
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
