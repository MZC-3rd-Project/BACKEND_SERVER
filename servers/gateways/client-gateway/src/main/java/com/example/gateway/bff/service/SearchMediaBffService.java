package com.example.gateway.bff.service;

import com.example.gateway.config.GatewaySecurityProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SearchMediaBffService {

    private final WebClient searchWebClient;
    private final WebClient mediaWebClient;
    private final GatewaySecurityProperties securityProperties;
    private final SearchThumbnailFallbackEnricher fallbackEnricher;
    private final ObjectMapper objectMapper;

    public SearchMediaBffService(
            WebClient.Builder webClientBuilder,
            GatewaySecurityProperties securityProperties,
            SearchThumbnailFallbackEnricher fallbackEnricher,
            ObjectMapper objectMapper,
            @Value("${app.service.search-url:http://localhost:8088}") String searchServiceUrl,
            @Value("${app.service.media-url:http://localhost:8094}") String mediaServiceUrl
    ) {
        this.searchWebClient = webClientBuilder.baseUrl(searchServiceUrl).build();
        this.mediaWebClient = webClientBuilder.baseUrl(mediaServiceUrl).build();
        this.securityProperties = securityProperties;
        this.fallbackEnricher = fallbackEnricher;
        this.objectMapper = objectMapper;
    }

    public Mono<ResponseEntity<JsonNode>> search(ServerHttpRequest request) {
        HttpHeaders downstreamHeaders = buildDownstreamHeaders();
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>(request.getQueryParams());

        return callSearch(queryParams, downstreamHeaders)
                .flatMap(response -> enrichWithMediaFallback(response, downstreamHeaders));
    }

    private Mono<ResponseEntity<JsonNode>> callSearch(MultiValueMap<String, String> queryParams,
                                                      HttpHeaders downstreamHeaders) {
        return searchWebClient.method(HttpMethod.GET)
                .uri(uriBuilder -> buildSearchUri(uriBuilder, queryParams))
                .headers(headers -> headers.addAll(downstreamHeaders))
                .exchangeToMono(response -> response.bodyToMono(JsonNode.class)
                        .defaultIfEmpty(objectMapper.createObjectNode())
                        .map(payload -> ResponseEntity.status(response.statusCode()).body(payload)));
    }

    private Mono<ResponseEntity<JsonNode>> enrichWithMediaFallback(ResponseEntity<JsonNode> searchResponse,
                                                                   HttpHeaders downstreamHeaders) {
        JsonNode body = searchResponse.getBody();
        if (!searchResponse.getStatusCode().is2xxSuccessful() || body == null) {
            return Mono.just(searchResponse);
        }

        List<Long> fallbackMediaIds = fallbackEnricher.collectFallbackMediaIds(body);
        if (fallbackMediaIds.isEmpty()) {
            return Mono.just(searchResponse);
        }

        return fetchMediaUrlMap(fallbackMediaIds, downstreamHeaders)
                .map(mediaUrlMap -> fallbackEnricher.applyFallbackUrls(body, mediaUrlMap))
                .map(enrichedBody -> ResponseEntity.status(searchResponse.getStatusCode()).body(enrichedBody))
                .onErrorResume(e -> {
                    log.warn("[SearchBff] thumbnail fallback failed. mediaCount={}", fallbackMediaIds.size(), e);
                    return Mono.just(searchResponse);
                });
    }

    private Mono<Map<Long, String>> fetchMediaUrlMap(List<Long> mediaIds, HttpHeaders downstreamHeaders) {
        return mediaWebClient.post()
                .uri("/internal/v1/media/urls/batch")
                .headers(headers -> headers.addAll(downstreamHeaders))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("mediaIds", mediaIds))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::toMediaUrlMap);
    }

    private Map<Long, String> toMediaUrlMap(JsonNode responseBody) {
        if (responseBody == null || !responseBody.path("success").asBoolean()) {
            return Map.of();
        }

        JsonNode data = responseBody.path("data");
        if (!data.isArray()) {
            return Map.of();
        }

        Map<Long, String> mediaUrlMap = new LinkedHashMap<>();
        for (JsonNode node : data) {
            long mediaId = node.path("mediaId").asLong(-1L);
            String mediaUrl = node.path("mediaUrl").asText(null);
            if (mediaId > 0 && StringUtils.hasText(mediaUrl)) {
                mediaUrlMap.put(mediaId, mediaUrl);
            }
        }
        return mediaUrlMap;
    }

    private java.net.URI buildSearchUri(UriBuilder uriBuilder, MultiValueMap<String, String> queryParams) {
        UriBuilder builder = uriBuilder.path("/api/v1/search");
        if (queryParams == null || queryParams.isEmpty()) {
            return builder.build();
        }

        queryParams.forEach((name, values) -> {
            if (!StringUtils.hasText(name) || values == null || values.isEmpty()) {
                return;
            }
            for (String value : values) {
                builder.queryParam(name, value);
            }
        });
        return builder.build();
    }

    private HttpHeaders buildDownstreamHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(securityProperties.getInternalAuthToken())
                && StringUtils.hasText(securityProperties.getInternalAuthHeader())) {
            headers.set(securityProperties.getInternalAuthHeader(), securityProperties.getInternalAuthToken());
        }
        return headers;
    }
}
