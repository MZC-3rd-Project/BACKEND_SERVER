package com.example.gateway.bff.service;

import com.example.contracts.http.HttpHeaderNames;
import com.example.gateway.bff.dto.catalog.CatalogItemsResponse;
import com.example.gateway.bff.dto.catalog.CatalogQueryParams;
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
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

@Slf4j
@Service
public class CatalogBffService {

    private static final String CODE_INVALID_REQUEST = "BFF-CATALOG-400";
    private static final String CODE_DOWNSTREAM_ERROR = "BFF-CATALOG-502";
    private static final String CODE_SEARCH_DISABLED = "BFF-CATALOG-503";
    private static final String DEGRADE_QUERY_PARAM = "degrade";

    private final WebClient searchWebClient;
    private final WebClient mediaWebClient;
    private final GatewaySessionPrincipalResolver sessionPrincipalResolver;
    private final GatewaySecurityProperties securityProperties;
    private final SearchThumbnailFallbackEnricher fallbackEnricher;
    private final CatalogResponseMapper responseMapper;
    private final CatalogMetricsService catalogMetricsService;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<HmacSigner> hmacSignerProvider;
    private final boolean searchEnabled;
    private final LongAdder degradeFallbackCounter = new LongAdder();

    public CatalogBffService(
            WebClient.Builder webClientBuilder,
            GatewaySessionPrincipalResolver sessionPrincipalResolver,
            GatewaySecurityProperties securityProperties,
            SearchThumbnailFallbackEnricher fallbackEnricher,
            CatalogResponseMapper responseMapper,
            CatalogMetricsService catalogMetricsService,
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
        this.responseMapper = responseMapper;
        this.catalogMetricsService = catalogMetricsService;
        this.objectMapper = objectMapper;
        this.hmacSignerProvider = hmacSignerProvider;
        this.searchEnabled = searchEnabled;
    }

    public Mono<ResponseEntity<CatalogItemsResponse>> listCatalogItems(ServerHttpRequest request) {
        if (!searchEnabled) {
            return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(CatalogItemsResponse.error(CODE_SEARCH_DISABLED, "검색 기능이 비활성화되었습니다")));
        }

        long startedAtNanos = System.nanoTime();
        CatalogQueryParams params;
        try {
            params = CatalogQueryParams.from(request);
        } catch (IllegalArgumentException error) {
            return Mono.just(badRequest(error.getMessage()));
        }

        boolean degradeRequested = isDegradeRequested(request);
        MultiValueMap<String, String> primaryQueryParams = params.toSearchQueryParams();

        return withOptionalUserContextHeaders(downstreamHeaders ->
                queryCatalog(primaryQueryParams, params, downstreamHeaders)
                        .onErrorResume(CatalogQueryException.class, error -> handleCatalogQueryFailure(
                                params,
                                downstreamHeaders,
                                degradeRequested,
                                error.reason(),
                                error.status(),
                                error.getMessage()
                        ))
                        .onErrorResume(error -> {
                            log.warn("[CatalogBff] catalog query failed with exception", error);
                            return handleCatalogQueryFailure(
                                    params,
                                    downstreamHeaders,
                                    degradeRequested,
                                    "primary_exception",
                                    HttpStatus.BAD_GATEWAY,
                                    "통합 목록 조회에 실패했습니다"
                            );
                        }))
                .doOnSuccess(response -> catalogMetricsService.recordQueryCompleted(
                        Duration.ofNanos(System.nanoTime() - startedAtNanos),
                        response
                ))
                .doOnError(error -> catalogMetricsService.recordQueryException(
                        Duration.ofNanos(System.nanoTime() - startedAtNanos),
                        "exception"
                ));
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
                    log.warn("[CatalogBff] thumbnail fallback failed. mediaCount={}", fallbackMediaIds.size(), error);
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

    private Mono<ResponseEntity<CatalogItemsResponse>> queryCatalog(MultiValueMap<String, String> queryParams,
                                                                    CatalogQueryParams params,
                                                                    HttpHeaders downstreamHeaders) {
        return callSearch(queryParams, downstreamHeaders)
                .flatMap(searchResponse -> enrichWithMediaFallback(searchResponse, downstreamHeaders))
                .flatMap(searchResponse -> mapCatalogResponse(searchResponse, params));
    }

    private Mono<ResponseEntity<CatalogItemsResponse>> mapCatalogResponse(ResponseEntity<JsonNode> searchResponse,
                                                                          CatalogQueryParams params) {
        if (!searchResponse.getStatusCode().is2xxSuccessful()) {
            HttpStatus status = HttpStatus.resolve(searchResponse.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.BAD_GATEWAY;
            }
            return Mono.error(new CatalogQueryException(
                    "primary_non_2xx",
                    status,
                    extractDownstreamErrorMessage(searchResponse.getBody())
            ));
        }

        try {
            CatalogItemsResponse response = responseMapper.toCatalogResponse(searchResponse.getBody(), params);
            return Mono.just(ResponseEntity.ok(response));
        } catch (IllegalArgumentException error) {
            return Mono.error(new CatalogQueryException(
                    "primary_mapping_error",
                    HttpStatus.BAD_GATEWAY,
                    error.getMessage()
            ));
        }
    }

    private Mono<ResponseEntity<CatalogItemsResponse>> handleCatalogQueryFailure(CatalogQueryParams params,
                                                                                 HttpHeaders downstreamHeaders,
                                                                                 boolean degradeRequested,
                                                                                 String reason,
                                                                                 HttpStatus status,
                                                                                 String message) {
        if (!degradeRequested) {
            if (status == HttpStatus.BAD_GATEWAY) {
                return Mono.just(badGateway(message));
            }
            return Mono.just(downstreamError(status, message));
        }

        long fallbackCount = incrementDegradeFallbackCount();
        catalogMetricsService.recordDegradeFallback(reason);
        log.warn("[CatalogBff] degrade fallback activated. reason={}, count={}, q={}, channel={}",
                reason, fallbackCount, params.query(), params.channel());

        return queryCatalog(params.toDegradeSearchQueryParams(), params, downstreamHeaders)
                .onErrorResume(CatalogQueryException.class, error -> {
                    log.warn("[CatalogBff] degrade query failed. reason={}, status={}", error.reason(), error.status().value());
                    if (error.status() == HttpStatus.BAD_GATEWAY) {
                        return Mono.just(badGateway(error.getMessage()));
                    }
                    return Mono.just(downstreamError(error.status(), error.getMessage()));
                })
                .onErrorResume(error -> {
                    log.warn("[CatalogBff] degrade query failed with exception. reason={}", reason, error);
                    return Mono.just(badGateway("통합 목록 조회에 실패했습니다"));
                });
    }

    private String extractDownstreamErrorMessage(JsonNode body) {
        if (body == null) {
            return "검색 서비스 호출에 실패했습니다";
        }

        String errorMessage = textOrNull(body.path("error").path("message"));
        if (StringUtils.hasText(errorMessage)) {
            return errorMessage;
        }
        String message = textOrNull(body.path("message"));
        if (StringUtils.hasText(message)) {
            return message;
        }
        return "검색 서비스 호출에 실패했습니다";
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean isDegradeRequested(ServerHttpRequest request) {
        String raw = request.getQueryParams().getFirst(DEGRADE_QUERY_PARAM);
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return "true".equals(normalized)
                || "1".equals(normalized)
                || "yes".equals(normalized)
                || "y".equals(normalized);
    }

    private long incrementDegradeFallbackCount() {
        degradeFallbackCounter.increment();
        return degradeFallbackCounter.sum();
    }

    private Mono<ResponseEntity<CatalogItemsResponse>> withOptionalUserContextHeaders(
            Function<HttpHeaders, Mono<ResponseEntity<CatalogItemsResponse>>> callback
    ) {
        return sessionPrincipalResolver.resolveFromSecurityContext()
                .map(Optional::of)
                .onErrorResume(SessionClaimParseException.class, error -> {
                    log.info("[CatalogBff] optional user context skipped. reason={}", error.getMessage());
                    return Mono.just(Optional.empty());
                })
                .defaultIfEmpty(Optional.empty())
                .flatMap(optionalPrincipal -> callback.apply(buildDownstreamHeaders(optionalPrincipal.orElse(null))));
    }

    private ResponseEntity<CatalogItemsResponse> badRequest(String message) {
        return ResponseEntity.badRequest()
                .body(CatalogItemsResponse.error(CODE_INVALID_REQUEST, message));
    }

    private ResponseEntity<CatalogItemsResponse> downstreamError(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(CatalogItemsResponse.error(CODE_DOWNSTREAM_ERROR, message));
    }

    private ResponseEntity<CatalogItemsResponse> badGateway(String message) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(CatalogItemsResponse.error(CODE_DOWNSTREAM_ERROR, message));
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

    private static final class CatalogQueryException extends RuntimeException {

        private final String reason;
        private final HttpStatus status;

        private CatalogQueryException(String reason, HttpStatus status, String message) {
            super(message);
            this.reason = reason;
            this.status = status;
        }

        private String reason() {
            return reason;
        }

        private HttpStatus status() {
            return status;
        }
    }
}
