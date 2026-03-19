package com.example.gateway.bff.service;

import com.example.contracts.http.HttpHeaderNames;
import com.example.gateway.config.GatewaySecurityProperties;
import com.example.gateway.security.GatewaySessionPrincipal;
import com.example.gateway.security.SessionClaimParseException;
import com.example.gateway.security.session.application.GatewaySessionPrincipalResolver;
import com.example.security.gateway.GatewayContextHeaderCodec;
import com.example.security.signature.HmacSigner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Service
public class SearchMediaBffService {

    private static final String CODE_SEARCH_DISABLED = "BFF-SEARCH-503";

    private final WebClient searchWebClient;
    private final WebClient mediaWebClient;
    private final GatewaySessionPrincipalResolver sessionPrincipalResolver;
    private final GatewaySecurityProperties securityProperties;
    private final SearchThumbnailFallbackEnricher fallbackEnricher;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<HmacSigner> hmacSignerProvider;
    private final boolean searchEnabled;

    public SearchMediaBffService(
            WebClient.Builder webClientBuilder,
            GatewaySessionPrincipalResolver sessionPrincipalResolver,
            GatewaySecurityProperties securityProperties,
            SearchThumbnailFallbackEnricher fallbackEnricher,
            ObjectMapper objectMapper,
            ObjectProvider<HmacSigner> hmacSignerProvider,
            @Value("${app.feature.search-enabled:true}") boolean searchEnabled,
            @Value("${app.service.search-url:http://localhost:8088}") String searchServiceUrl,
            @Value("${app.service.media-url:http://localhost:8094}") String mediaServiceUrl
    ) {
        this.searchWebClient = webClientBuilder.baseUrl(searchServiceUrl).build();
        this.mediaWebClient = webClientBuilder.baseUrl(mediaServiceUrl).build();
        this.sessionPrincipalResolver = sessionPrincipalResolver;
        this.securityProperties = securityProperties;
        this.fallbackEnricher = fallbackEnricher;
        this.objectMapper = objectMapper;
        this.hmacSignerProvider = hmacSignerProvider;
        this.searchEnabled = searchEnabled;
    }

    public Mono<ResponseEntity<JsonNode>> search(ServerHttpRequest request) {
        if (!searchEnabled) {
            return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(searchDisabledBody()));
        }

        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>(request.getQueryParams());
        return withOptionalUserContextHeaders(downstreamHeaders ->
                callSearch(queryParams, downstreamHeaders)
                        .flatMap(response -> enrichWithMediaFallback(response, downstreamHeaders)));
    }

    public Mono<ResponseEntity<JsonNode>> trackClick(JsonNode requestBody) {
        if (!searchEnabled) {
            return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(searchDisabledBody()));
        }

        JsonNode safeRequestBody = requestBody == null ? objectMapper.createObjectNode() : requestBody;
        return withOptionalUserContextHeaders(downstreamHeaders ->
                searchWebClient.post()
                        .uri("/api/v1/search/clicks")
                        .headers(headers -> headers.addAll(downstreamHeaders))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(safeRequestBody)
                        .exchangeToMono(response -> response.bodyToMono(JsonNode.class)
                                .defaultIfEmpty(objectMapper.createObjectNode())
                                .map(payload -> ResponseEntity.status(response.statusCode()).body(payload))));
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
                .onErrorResume(error -> {
                    log.warn("[SearchBff] thumbnail fallback failed. mediaCount={}", fallbackMediaIds.size(), error);
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

    private Mono<ResponseEntity<JsonNode>> withOptionalUserContextHeaders(
            Function<HttpHeaders, Mono<ResponseEntity<JsonNode>>> callback
    ) {
        return sessionPrincipalResolver.resolveFromSecurityContext()
                .map(Optional::of)
                .onErrorResume(SessionClaimParseException.class, error -> {
                    log.info("[SearchBff] optional user context skipped. reason={}", error.getMessage());
                    return Mono.just(Optional.empty());
                })
                .defaultIfEmpty(Optional.empty())
                .flatMap(optionalPrincipal -> callback.apply(buildDownstreamHeaders(optionalPrincipal.orElse(null))));
    }

    private JsonNode searchDisabledBody() {
        var body = objectMapper.createObjectNode();
        body.put("success", false);
        body.putNull("data");
        body.putObject("error")
                .put("code", CODE_SEARCH_DISABLED)
                .put("message", "검색 기능이 비활성화되었습니다");
        return body;
    }

    private HttpHeaders buildDownstreamHeaders(GatewaySessionPrincipal principal) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(securityProperties.getInternalAuthToken())
                && StringUtils.hasText(securityProperties.getInternalAuthHeader())) {
            headers.set(securityProperties.getInternalAuthHeader(), securityProperties.getInternalAuthToken());
        }
        if (principal == null) {
            return headers;
        }

        headers.set(HttpHeaderNames.USER_ID, String.valueOf(principal.userId()));
        headers.set(HttpHeaderNames.USER_ROLES, principal.rolesHeaderValue());
        if (StringUtils.hasText(principal.sessionId())) {
            headers.set(HttpHeaderNames.SESSION_ID, principal.sessionId());
        }

        String gatewayContext = createSignedContextHeader(principal);
        if (StringUtils.hasText(gatewayContext)) {
            headers.set(HttpHeaderNames.GATEWAY_CONTEXT, gatewayContext);
        }
        return headers;
    }

    private String createSignedContextHeader(GatewaySessionPrincipal principal) {
        HmacSigner signer = hmacSignerProvider.getIfAvailable();
        if (signer == null) {
            return null;
        }

        String userId = String.valueOf(principal.userId());
        String roles = principal.rolesHeaderValue();
        String nonce = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();
        return GatewayContextHeaderCodec.encodeSigned(userId, roles, nonce, timestamp, signer);
    }
}
