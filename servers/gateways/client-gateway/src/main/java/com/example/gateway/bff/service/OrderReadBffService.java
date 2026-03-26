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
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Service
public class OrderReadBffService {

    private static final String CODE_INVALID_REQUEST = "BFF-ORDER-400";
    private static final String CODE_DOWNSTREAM_ERROR = "BFF-ORDER-502";

    private final WebClient orderWebClient;
    private final WebClient productWebClient;
    private final WebClient mediaWebClient;
    private final GatewaySessionPrincipalResolver sessionPrincipalResolver;
    private final GatewaySecurityProperties securityProperties;
    private final ObjectProvider<HmacSigner> hmacSignerProvider;
    private final ObjectMapper objectMapper;

    public OrderReadBffService(
            WebClient.Builder webClientBuilder,
            GatewaySessionPrincipalResolver sessionPrincipalResolver,
            GatewaySecurityProperties securityProperties,
            ObjectProvider<HmacSigner> hmacSignerProvider,
            ObjectMapper objectMapper,
            @Value("${app.service.order-url:http://localhost:8090}") String orderServiceUrl,
            @Value("${app.service.product-url:http://localhost:8084}") String productServiceUrl,
            @Value("${app.service.media-url:http://localhost:8094}") String mediaServiceUrl
    ) {
        this.orderWebClient = webClientBuilder.baseUrl(orderServiceUrl).build();
        this.productWebClient = webClientBuilder.baseUrl(productServiceUrl).build();
        this.mediaWebClient = webClientBuilder.baseUrl(mediaServiceUrl).build();
        this.sessionPrincipalResolver = sessionPrincipalResolver;
        this.securityProperties = securityProperties;
        this.hmacSignerProvider = hmacSignerProvider;
        this.objectMapper = objectMapper;
    }

    public Mono<ResponseEntity<JsonNode>> getOrderDetail(Long orderId) {
        if (orderId == null || orderId <= 0) {
            return Mono.just(badRequest("orderId는 양수여야 합니다"));
        }

        return withAuthHeaders(headers ->
                callGet(orderWebClient, "/api/v1/orders/" + orderId, headers)
                        .flatMap(orderResponse -> enrichOrderDetail(orderId, orderResponse, headers))
                        .onErrorResume(error -> {
                            log.warn("Order detail lookup failed. orderId={}", orderId, error);
                            return Mono.just(badGateway("주문 상세 조회에 실패했습니다"));
                        }));
    }

    private Mono<ResponseEntity<JsonNode>> enrichOrderDetail(Long orderId,
                                                             ResponseEntity<JsonNode> orderResponse,
                                                             HttpHeaders headers) {
        if (!isSuccessResponse(orderResponse)) {
            return Mono.just(normalizeSnowflakeIds(orderResponse));
        }

        ObjectNode orderData = dataObject(orderResponse);
        if (orderData == null) {
            return Mono.just(normalizeSnowflakeIds(orderResponse));
        }

        JsonNode orderItemsNode = orderData.path("items");
        if (!orderItemsNode.isArray()) {
            return Mono.just(normalizeSnowflakeIds(orderResponse));
        }

        List<Long> itemIds = new ArrayList<>();
        for (JsonNode orderItemNode : orderItemsNode) {
            Long itemId = positiveLong(orderItemNode.path("itemId"), null);
            if (itemId != null) {
                itemIds.add(itemId);
            }
        }

        return fetchItemSummaryMap(itemIds, headers)
                .flatMap(itemSummaryMap -> {
                    List<Long> mediaIds = extractThumbnailMediaIds(itemSummaryMap.values());
                    return fetchMediaUrlMap(mediaIds, headers)
                            .map(mediaUrlMap -> {
                                mergeItemMetadata(orderItemsNode, itemSummaryMap, mediaUrlMap);
                                return normalizeSnowflakeIds(orderResponse);
                            });
                })
                .onErrorResume(error -> {
                    log.warn("Order detail enrichment skipped. orderId={}", orderId, error);
                    return Mono.just(normalizeSnowflakeIds(orderResponse));
                });
    }

    private void mergeItemMetadata(JsonNode orderItemsNode,
                                   Map<Long, ObjectNode> itemSummaryMap,
                                   Map<Long, String> mediaUrlMap) {
        if (!orderItemsNode.isArray() || itemSummaryMap == null || itemSummaryMap.isEmpty()) {
            return;
        }

        for (JsonNode orderItemNode : orderItemsNode) {
            if (!(orderItemNode instanceof ObjectNode orderItem)) {
                continue;
            }
            Long itemId = positiveLong(orderItem.path("itemId"), null);
            if (itemId == null) {
                continue;
            }

            ObjectNode itemSummary = itemSummaryMap.get(itemId);
            if (itemSummary == null) {
                continue;
            }

            String productName = textOrNull(itemSummary.path("title"));
            Long thumbnailMediaId = positiveLong(itemSummary.path("images").path("thumbnail").path("mediaId"), null);
            String thumbnailUrl = thumbnailMediaId == null ? null : mediaUrlMap.get(thumbnailMediaId);

            putIfBlank(orderItem, "name", productName);
            putIfBlank(orderItem, "productName", productName);
            putIfBlank(orderItem, "itemName", productName);
            putIfNull(orderItem, "thumbnailMediaId", thumbnailMediaId);
            putIfBlank(orderItem, "thumbnailUrl", thumbnailUrl);
            putIfBlank(orderItem, "thumbnail", thumbnailUrl);
        }
    }

    private Mono<ResponseEntity<JsonNode>> callGet(WebClient webClient,
                                                   String path,
                                                   HttpHeaders headers) {
        return webClient.method(HttpMethod.GET)
                .uri(path)
                .headers(targetHeaders -> targetHeaders.addAll(headers))
                .exchangeToMono(response -> response.bodyToMono(JsonNode.class)
                        .defaultIfEmpty(objectMapper.createObjectNode())
                        .map(payload -> ResponseEntity.status(response.statusCode()).body(payload)));
    }

    private Mono<ResponseEntity<JsonNode>> callPost(WebClient webClient,
                                                    String path,
                                                    Object requestBody,
                                                    HttpHeaders headers) {
        return webClient.method(HttpMethod.POST)
                .uri(path)
                .headers(targetHeaders -> targetHeaders.addAll(headers))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchangeToMono(response -> response.bodyToMono(JsonNode.class)
                        .defaultIfEmpty(objectMapper.createObjectNode())
                        .map(payload -> ResponseEntity.status(response.statusCode()).body(payload)));
    }

    private Mono<ResponseEntity<JsonNode>> withAuthHeaders(
            Function<HttpHeaders, Mono<ResponseEntity<JsonNode>>> callback) {
        return sessionPrincipalResolver.resolveFromSecurityContext()
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(optionalPrincipal -> {
                    if (optionalPrincipal.isEmpty()) {
                        return Mono.just(unauthorized("GW-AUTH-003", "요청 경로는 인증 정보가 필요합니다"));
                    }
                    return callback.apply(buildDownstreamHeaders(optionalPrincipal.get()));
                })
                .onErrorResume(SessionClaimParseException.class,
                        error -> Mono.just(unauthorized("GW-AUTH-008", error.getMessage())));
    }

    private HttpHeaders buildDownstreamHeaders(GatewaySessionPrincipal principal) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(securityProperties.getInternalAuthToken())
                && StringUtils.hasText(securityProperties.getInternalAuthHeader())) {
            headers.set(securityProperties.getInternalAuthHeader(), securityProperties.getInternalAuthToken());
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

    private boolean isSuccessResponse(ResponseEntity<JsonNode> response) {
        if (response == null || !response.getStatusCode().is2xxSuccessful()) {
            return false;
        }
        JsonNode body = response.getBody();
        return body != null && body.path("success").asBoolean(false);
    }

    private JsonNode dataNode(ResponseEntity<JsonNode> response) {
        if (!isSuccessResponse(response)) {
            return objectMapper.createObjectNode();
        }
        JsonNode body = response.getBody();
        if (body == null) {
            return objectMapper.createObjectNode();
        }
        return body.path("data");
    }

    private ObjectNode dataObject(ResponseEntity<JsonNode> response) {
        if (!isSuccessResponse(response)) {
            return null;
        }
        JsonNode body = response.getBody();
        if (body == null) {
            return null;
        }
        JsonNode data = body.path("data");
        return data instanceof ObjectNode objectNode ? objectNode : null;
    }

    private Long positiveLong(JsonNode node, Long fallback) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return fallback;
        }
        if (node.isIntegralNumber()) {
            long value = node.asLong();
            return value >= 0 ? value : fallback;
        }
        if (node.isTextual()) {
            String raw = node.asText(null);
            if (!StringUtils.hasText(raw)) {
                return fallback;
            }
            try {
                long value = Long.parseLong(raw.trim());
                return value >= 0 ? value : fallback;
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String value = node.asText(null);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private Mono<Map<Long, ObjectNode>> fetchItemSummaryMap(List<Long> itemIds,
                                                            HttpHeaders headers) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Mono.just(Map.of());
        }
        List<Long> distinctIds = itemIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (distinctIds.isEmpty()) {
            return Mono.just(Map.of());
        }

        return callPost(productWebClient, "/internal/v1/items/batch", distinctIds, headers)
                .map(response -> {
                    if (!isSuccessResponse(response)) {
                        return Map.<Long, ObjectNode>of();
                    }
                    JsonNode data = dataNode(response);
                    if (!data.isArray()) {
                        return Map.<Long, ObjectNode>of();
                    }

                    Map<Long, ObjectNode> result = new LinkedHashMap<>();
                    for (JsonNode node : data) {
                        if (!(node instanceof ObjectNode objectNode)) {
                            continue;
                        }
                        Long itemId = positiveLong(objectNode.path("id"), null);
                        if (itemId == null) {
                            continue;
                        }
                        result.put(itemId, objectNode);
                    }
                    return Map.copyOf(result);
                })
                .onErrorReturn(Map.of());
    }

    private Mono<Map<Long, String>> fetchMediaUrlMap(List<Long> mediaIds,
                                                     HttpHeaders headers) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return Mono.just(Map.of());
        }
        List<Long> distinctIds = mediaIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (distinctIds.isEmpty()) {
            return Mono.just(Map.of());
        }

        return callPost(mediaWebClient, "/internal/v1/media/urls/batch", Map.of("mediaIds", distinctIds), headers)
                .map(response -> {
                    if (!isSuccessResponse(response)) {
                        return Map.<Long, String>of();
                    }
                    JsonNode data = dataNode(response);
                    if (!data.isArray()) {
                        return Map.<Long, String>of();
                    }

                    Map<Long, String> result = new LinkedHashMap<>();
                    for (JsonNode node : data) {
                        Long mediaId = positiveLong(node.path("mediaId"), null);
                        String mediaUrl = textOrNull(node.path("mediaUrl"));
                        if (mediaId == null || !StringUtils.hasText(mediaUrl)) {
                            continue;
                        }
                        result.put(mediaId, mediaUrl);
                    }
                    return Map.copyOf(result);
                })
                .onErrorReturn(Map.of());
    }

    private List<Long> extractThumbnailMediaIds(Iterable<ObjectNode> itemSummaries) {
        List<Long> mediaIds = new ArrayList<>();
        if (itemSummaries == null) {
            return mediaIds;
        }
        for (ObjectNode itemSummary : itemSummaries) {
            if (itemSummary == null) {
                continue;
            }
            Long mediaId = positiveLong(itemSummary.path("images").path("thumbnail").path("mediaId"), null);
            if (mediaId != null) {
                mediaIds.add(mediaId);
            }
        }
        return mediaIds;
    }

    private void putIfBlank(ObjectNode node, String fieldName, String value) {
        if (node == null || !StringUtils.hasText(fieldName) || !StringUtils.hasText(value)) {
            return;
        }
        String currentValue = textOrNull(node.path(fieldName));
        if (!StringUtils.hasText(currentValue)) {
            node.put(fieldName, value);
        }
    }

    private void putIfNull(ObjectNode node, String fieldName, Long value) {
        if (node == null || !StringUtils.hasText(fieldName) || value == null) {
            return;
        }
        if (!node.hasNonNull(fieldName)) {
            node.put(fieldName, value);
        }
    }

    private ResponseEntity<JsonNode> badRequest(String message) {
        return errorResponse(HttpStatus.BAD_REQUEST, CODE_INVALID_REQUEST, message);
    }

    private ResponseEntity<JsonNode> badGateway(String message) {
        return errorResponse(HttpStatus.BAD_GATEWAY, CODE_DOWNSTREAM_ERROR, message);
    }

    private ResponseEntity<JsonNode> unauthorized(String code, String message) {
        return errorResponse(HttpStatus.UNAUTHORIZED, code, message);
    }

    private ResponseEntity<JsonNode> errorResponse(HttpStatus status, String code, String message) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("success", false);
        ObjectNode error = body.putObject("error");
        error.put("code", code);
        error.put("message", message);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }

    private ResponseEntity<JsonNode> normalizeSnowflakeIds(ResponseEntity<JsonNode> response) {
        if (response == null) {
            return null;
        }
        SnowflakeJsonFieldNormalizer.normalizeSuccessData(response.getBody());
        return response;
    }
}
