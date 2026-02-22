package com.example.gateway.bff.service;

import com.example.contracts.http.HttpHeaderNames;
import com.example.gateway.config.GatewaySecurityProperties;
import com.example.gateway.bff.dto.BffItemCreateCommandRequest;
import com.example.gateway.bff.dto.BffItemImageRequest;
import com.example.gateway.bff.dto.BffItemType;
import com.example.gateway.bff.dto.BffItemUpdateCommandRequest;
import com.example.gateway.security.GatewayJwtPrincipal;
import com.example.gateway.security.JwtClaimParseException;
import com.example.gateway.security.JwtClaimParser;
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
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
public class ProductMediaBffService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final JwtClaimParser jwtClaimParser;
    private final GatewaySecurityProperties securityProperties;
    private final ObjectProvider<HmacSigner> hmacSignerProvider;

    public ProductMediaBffService(WebClient.Builder webClientBuilder,
                                  ObjectMapper objectMapper,
                                  JwtClaimParser jwtClaimParser,
                                  GatewaySecurityProperties securityProperties,
                                  ObjectProvider<HmacSigner> hmacSignerProvider,
                                  @Value("${app.service.product-url:http://localhost:8084}") String productServiceUrl) {
        this.webClient = webClientBuilder
                .baseUrl(productServiceUrl)
                .build();
        this.objectMapper = objectMapper;
        this.jwtClaimParser = jwtClaimParser;
        this.securityProperties = securityProperties;
        this.hmacSignerProvider = hmacSignerProvider;
    }

    public Mono<ResponseEntity<JsonNode>> createProductWithMedia(BffItemCreateCommandRequest request, HttpHeaders inboundHeaders) {
        return createItemWithMedia(BffItemType.PRODUCT, request, inboundHeaders);
    }

    public Mono<ResponseEntity<JsonNode>> createGoodsWithMedia(BffItemCreateCommandRequest request, HttpHeaders inboundHeaders) {
        return createItemWithMedia(BffItemType.GOODS, request, inboundHeaders);
    }

    public Mono<ResponseEntity<JsonNode>> createPerformanceWithMedia(BffItemCreateCommandRequest request, HttpHeaders inboundHeaders) {
        return createItemWithMedia(BffItemType.PERFORMANCE, request, inboundHeaders);
    }

    public Mono<ResponseEntity<JsonNode>> updateProductWithMedia(Long itemId,
                                                                 BffItemUpdateCommandRequest request,
                                                                 HttpHeaders inboundHeaders) {
        return updateItemWithMedia(BffItemType.PRODUCT, itemId, request, inboundHeaders);
    }

    public Mono<ResponseEntity<JsonNode>> updateGoodsWithMedia(Long itemId,
                                                               BffItemUpdateCommandRequest request,
                                                               HttpHeaders inboundHeaders) {
        return updateItemWithMedia(BffItemType.GOODS, itemId, request, inboundHeaders);
    }

    public Mono<ResponseEntity<JsonNode>> updatePerformanceWithMedia(Long itemId,
                                                                     BffItemUpdateCommandRequest request,
                                                                     HttpHeaders inboundHeaders) {
        return updateItemWithMedia(BffItemType.PERFORMANCE, itemId, request, inboundHeaders);
    }

    public Mono<ResponseEntity<JsonNode>> findItemDetail(String typeValue, Long itemId, HttpHeaders inboundHeaders) {
        BffItemType itemType = parseItemType(typeValue);
        if (itemType == null) {
            return Mono.just(badRequest("type은 PRODUCT, GOODS, PERFORMANCE 중 하나여야 합니다"));
        }
        if (itemId == null || itemId <= 0) {
            return Mono.just(badRequest("itemId는 양수여야 합니다"));
        }
        return withAuthHeaders(inboundHeaders, false, downstreamHeaders ->
                callDownstream(HttpMethod.GET, itemType.collectionPath() + "/" + itemId, downstreamHeaders, null));
    }

    public Mono<ResponseEntity<JsonNode>> findItemList(String typeValue, String cursor, Integer size, HttpHeaders inboundHeaders) {
        BffItemType itemType = parseItemType(typeValue);
        if (itemType == null) {
            return Mono.just(badRequest("type은 PRODUCT, GOODS, PERFORMANCE 중 하나여야 합니다"));
        }

        int normalizedSize = size == null ? 20 : size;
        if (normalizedSize < 1 || normalizedSize > 100) {
            return Mono.just(badRequest("size는 1 이상 100 이하이어야 합니다"));
        }

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath(itemType.collectionPath())
                .queryParam("size", normalizedSize);
        if (StringUtils.hasText(cursor)) {
            uriBuilder.queryParam("cursor", cursor);
        }
        String pathWithQuery = uriBuilder.build(true).toUriString();
        return withAuthHeaders(inboundHeaders, false, downstreamHeaders ->
                callDownstream(HttpMethod.GET, pathWithQuery, downstreamHeaders, null));
    }

    private Mono<ResponseEntity<JsonNode>> createItemWithMedia(BffItemType itemType,
                                                               BffItemCreateCommandRequest request,
                                                               HttpHeaders inboundHeaders) {
        if (request == null || request.getItem() == null || request.getItem().isNull()) {
            return Mono.just(badRequest("item payload는 필수입니다"));
        }

        return withAuthHeaders(inboundHeaders, true, downstreamHeaders ->
                callDownstream(HttpMethod.POST, itemType.collectionPath(), downstreamHeaders, request.getItem())
                        .flatMap(createResponse -> {
                            if (!createResponse.getStatusCode().is2xxSuccessful()) {
                                return Mono.just(createResponse);
                            }

                            Long itemId = extractItemId(createResponse.getBody());
                            if (itemId == null || itemId <= 0) {
                                return Mono.just(gatewayError("BFF-ITEM-001", "생성 응답에서 itemId를 찾지 못했습니다"));
                            }

                            List<BffItemImageRequest> images = sanitizeImageRequests(request.getImages());
                            if (images.isEmpty()) {
                                return fetchItemDetail(itemType, itemId, downstreamHeaders, createResponse);
                            }

                            return ensureSuccess(callDownstream(HttpMethod.POST,
                                    "/api/items/" + itemId + "/images",
                                    downstreamHeaders,
                                    images))
                                    .then(fetchItemDetail(itemType, itemId, downstreamHeaders, createResponse))
                                    .onErrorResume(DownstreamCallException.class, e -> Mono.just(e.response()));
                        }));
    }

    private Mono<ResponseEntity<JsonNode>> updateItemWithMedia(BffItemType itemType,
                                                               Long itemId,
                                                               BffItemUpdateCommandRequest request,
                                                               HttpHeaders inboundHeaders) {
        if (itemId == null || itemId <= 0) {
            return Mono.just(badRequest("itemId는 양수여야 합니다"));
        }
        if (request == null || request.getItem() == null || request.getItem().isNull()) {
            return Mono.just(badRequest("item payload는 필수입니다"));
        }

        return withAuthHeaders(inboundHeaders, true, downstreamHeaders ->
                callDownstream(HttpMethod.PUT, itemType.collectionPath() + "/" + itemId, downstreamHeaders, request.getItem())
                        .flatMap(updateResponse -> {
                            if (!updateResponse.getStatusCode().is2xxSuccessful()) {
                                return Mono.just(updateResponse);
                            }

                            return applyImageOperations(itemId, request, downstreamHeaders)
                                    .then(fetchItemDetail(itemType, itemId, downstreamHeaders, updateResponse))
                                    .onErrorResume(DownstreamCallException.class, e -> Mono.just(e.response()));
                        }));
    }

    private Mono<Void> applyImageOperations(Long itemId,
                                            BffItemUpdateCommandRequest request,
                                            HttpHeaders downstreamHeaders) {
        Mono<Void> chain = Mono.empty();

        List<BffItemImageRequest> addImages = sanitizeImageRequests(request.getAddImages());
        if (!addImages.isEmpty()) {
            chain = chain.then(ensureSuccess(callDownstream(
                    HttpMethod.POST,
                    "/api/items/" + itemId + "/images",
                    downstreamHeaders,
                    addImages
            )));
        }

        List<Long> deleteImageIds = sanitizePositiveLongList(request.getDeleteImageIds());
        if (!deleteImageIds.isEmpty()) {
            chain = chain.then(Flux.fromIterable(deleteImageIds)
                    .concatMap(imageId -> ensureSuccess(callDownstream(
                            HttpMethod.DELETE,
                            "/api/items/images/" + imageId,
                            downstreamHeaders,
                            null
                    )))
                    .then());
        }

        List<Long> reorderImageIds = sanitizePositiveLongList(request.getReorderImageIds());
        if (!reorderImageIds.isEmpty()) {
            chain = chain.then(ensureSuccess(callDownstream(
                    HttpMethod.PUT,
                    "/api/items/" + itemId + "/images/reorder",
                    downstreamHeaders,
                    reorderImageIds
            )));
        }

        return chain;
    }

    private Mono<ResponseEntity<JsonNode>> fetchItemDetail(BffItemType itemType,
                                                           Long itemId,
                                                           HttpHeaders downstreamHeaders,
                                                           ResponseEntity<JsonNode> fallbackResponse) {
        return callDownstream(HttpMethod.GET, itemType.collectionPath() + "/" + itemId, downstreamHeaders, null)
                .map(detailResponse -> detailResponse.getStatusCode().is2xxSuccessful() ? detailResponse : fallbackResponse)
                .onErrorReturn(fallbackResponse);
    }

    private Mono<Void> ensureSuccess(Mono<ResponseEntity<JsonNode>> call) {
        return call.flatMap(response -> {
            if (response.getStatusCode().is2xxSuccessful()) {
                return Mono.empty();
            }
            return Mono.error(new DownstreamCallException(response));
        });
    }

    private Mono<ResponseEntity<JsonNode>> callDownstream(HttpMethod method,
                                                          String path,
                                                          HttpHeaders downstreamHeaders,
                                                          Object body) {
        WebClient.RequestBodySpec requestSpec = webClient.method(method)
                .uri(path)
                .headers(headers -> headers.addAll(downstreamHeaders));

        WebClient.RequestHeadersSpec<?> headersSpec = body != null
                ? requestSpec.contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                : requestSpec;

        return headersSpec.exchangeToMono(response -> response.bodyToMono(JsonNode.class)
                .defaultIfEmpty(objectMapper.createObjectNode())
                .map(payload -> ResponseEntity.status(response.statusCode()).body(payload)));
    }

    private Mono<ResponseEntity<JsonNode>> withAuthHeaders(HttpHeaders inboundHeaders,
                                                           boolean jwtRequired,
                                                           java.util.function.Function<HttpHeaders, Mono<ResponseEntity<JsonNode>>> callback) {
        try {
            HttpHeaders downstreamHeaders = buildDownstreamHeaders(inboundHeaders, jwtRequired);
            return callback.apply(downstreamHeaders);
        } catch (AuthHeaderException e) {
            return Mono.just(unauthorized(e.code(), e.getMessage()));
        }
    }

    private HttpHeaders buildDownstreamHeaders(HttpHeaders inboundHeaders, boolean jwtRequired) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        applyInternalAuthHeader(headers);

        String bearerToken = resolveBearerToken(inboundHeaders);
        if (!StringUtils.hasText(bearerToken)) {
            if (jwtRequired) {
                throw new AuthHeaderException("GW-AUTH-003", "요청 경로는 Bearer 토큰이 필요합니다");
            }
            return headers;
        }

        GatewayJwtPrincipal principal;
        try {
            principal = jwtClaimParser.parse(bearerToken);
        } catch (JwtClaimParseException e) {
            throw new AuthHeaderException("GW-AUTH-002", e.getMessage());
        }

        headers.set(HttpHeaderNames.USER_ID, String.valueOf(principal.userId()));
        headers.set(HttpHeaderNames.USER_ROLES, principal.rolesHeaderValue());

        String gatewayContext = createSignedContextHeader(principal);
        if (StringUtils.hasText(gatewayContext)) {
            headers.set(HttpHeaderNames.GATEWAY_CONTEXT, gatewayContext);
        }

        return headers;
    }

    private void applyInternalAuthHeader(HttpHeaders headers) {
        if (StringUtils.hasText(securityProperties.getInternalAuthToken())
                && StringUtils.hasText(securityProperties.getInternalAuthHeader())) {
            headers.set(securityProperties.getInternalAuthHeader(), securityProperties.getInternalAuthToken());
        }
    }

    private String resolveBearerToken(HttpHeaders inboundHeaders) {
        if (inboundHeaders == null || inboundHeaders.isEmpty()) {
            return null;
        }
        String authHeader = inboundHeaders.getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader)) {
            return null;
        }

        String prefix = "bearer ";
        if (!authHeader.toLowerCase(Locale.ROOT).startsWith(prefix)) {
            throw new AuthHeaderException("GW-AUTH-001", "Authorization 헤더는 Bearer 형식이어야 합니다");
        }

        String token = authHeader.substring(prefix.length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new AuthHeaderException("GW-AUTH-001", "Bearer 토큰이 비어 있습니다");
        }
        return token;
    }

    private String createSignedContextHeader(GatewayJwtPrincipal principal) {
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

    private List<BffItemImageRequest> sanitizeImageRequests(List<BffItemImageRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        return requests.stream()
                .filter(request -> request != null && request.getMediaId() != null && request.getMediaId() > 0)
                .toList();
    }

    private List<Long> sanitizePositiveLongList(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && value > 0)
                .toList();
    }

    private Long extractItemId(JsonNode body) {
        if (body == null || body.isNull()) {
            return null;
        }
        JsonNode idNode = body.path("data").path("id");
        if (idNode.isIntegralNumber()) {
            return idNode.asLong();
        }
        if (idNode.isTextual()) {
            String raw = idNode.asText();
            if (StringUtils.hasText(raw)) {
                try {
                    return Long.parseLong(raw);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private ResponseEntity<JsonNode> badRequest(String message) {
        return errorResponse(HttpStatus.BAD_REQUEST, "BFF-ITEM-400", message);
    }

    private ResponseEntity<JsonNode> gatewayError(String code, String message) {
        return errorResponse(HttpStatus.BAD_GATEWAY, code, message);
    }

    private ResponseEntity<JsonNode> unauthorized(String code, String message) {
        return errorResponse(HttpStatus.UNAUTHORIZED, code, message);
    }

    private BffItemType parseItemType(String typeValue) {
        try {
            return BffItemType.fromNullable(typeValue);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ResponseEntity<JsonNode> errorResponse(HttpStatus status, String code, String message) {
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode error = objectMapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);

        payload.put("success", false);
        payload.set("error", error);
        payload.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(payload);
    }

    private static final class DownstreamCallException extends RuntimeException {

        private final ResponseEntity<JsonNode> response;

        private DownstreamCallException(ResponseEntity<JsonNode> response) {
            this.response = response;
        }

        private ResponseEntity<JsonNode> response() {
            return response;
        }
    }

    private static final class AuthHeaderException extends RuntimeException {

        private final String code;

        private AuthHeaderException(String code, String message) {
            super(message);
            this.code = code;
        }

        private String code() {
            return code;
        }
    }
}
