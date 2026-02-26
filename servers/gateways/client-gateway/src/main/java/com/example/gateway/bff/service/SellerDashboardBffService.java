package com.example.gateway.bff.service;

import com.example.contracts.http.HttpHeaderNames;
import com.example.gateway.config.GatewaySecurityProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Service
public class SellerDashboardBffService {

    private static final String CODE_INVALID_REQUEST = "BFF-DASHBOARD-400";
    private static final String CODE_DOWNSTREAM_ERROR = "BFF-DASHBOARD-502";

    private final WebClient analyticsDashboardWebClient;
    private final GatewaySecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    public SellerDashboardBffService(
            WebClient.Builder webClientBuilder,
            GatewaySecurityProperties securityProperties,
            ObjectMapper objectMapper,
            @Value("${app.service.analytics-dashboard-url:http://localhost:8095}") String analyticsDashboardServiceUrl
    ) {
        this.analyticsDashboardWebClient = webClientBuilder.baseUrl(analyticsDashboardServiceUrl).build();
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
    }

    public Mono<ResponseEntity<JsonNode>> getOverview(ServerHttpRequest request,
                                                      String storeId,
                                                      String mode,
                                                      String date,
                                                      String yearMonth,
                                                      String from,
                                                      String to,
                                                      String bucket,
                                                      String timezone) {
        Long sellerId = parseSellerId(request);
        if (sellerId == null) {
            return Mono.just(badRequest("X-User-Id 헤더가 없거나 유효하지 않습니다."));
        }
        Long resolvedStoreId = resolveStoreId(request, storeId, sellerId);
        if (resolvedStoreId == null) {
            return Mono.just(badRequest("storeId는 양수여야 합니다."));
        }

        HttpHeaders downstreamHeaders = buildDownstreamHeaders(sellerId, resolvedStoreId);
        return analyticsDashboardWebClient.get()
                .uri(uriBuilder -> buildOverviewUri(
                        uriBuilder,
                        resolvedStoreId,
                        mode,
                        date,
                        yearMonth,
                        from,
                        to,
                        bucket,
                        timezone
                ))
                .headers(headers -> headers.addAll(downstreamHeaders))
                .exchangeToMono(response -> response.bodyToMono(JsonNode.class)
                        .defaultIfEmpty(objectMapper.createObjectNode())
                        .map(payload -> ResponseEntity.status(response.statusCode()).body(payload)))
                .onErrorResume(e -> {
                    log.warn("[SellerDashboardBff] overview query failed. sellerId={}, storeId={}",
                            sellerId,
                            resolvedStoreId,
                            e);
                    return Mono.just(badGateway("셀러 대시보드 조회에 실패했습니다."));
                });
    }

    private java.net.URI buildOverviewUri(UriBuilder uriBuilder,
                                          Long storeId,
                                          String mode,
                                          String date,
                                          String yearMonth,
                                          String from,
                                          String to,
                                          String bucket,
                                          String timezone) {
        UriBuilder builder = uriBuilder.path("/internal/v1/analytics/stores/{storeId}/dashboard/overview");
        addQueryParam(builder, "mode", mode);
        addQueryParam(builder, "date", date);
        addQueryParam(builder, "yearMonth", yearMonth);
        addQueryParam(builder, "from", from);
        addQueryParam(builder, "to", to);
        addQueryParam(builder, "bucket", bucket);
        addQueryParam(builder, "timezone", timezone);
        return builder.build(storeId);
    }

    private void addQueryParam(UriBuilder builder, String name, String value) {
        if (StringUtils.hasText(value)) {
            builder.queryParam(name, value);
        }
    }

    private Long parseSellerId(ServerHttpRequest request) {
        String userIdHeader = request.getHeaders().getFirst(HttpHeaderNames.USER_ID);
        return parsePositiveLong(userIdHeader);
    }

    private Long resolveStoreId(ServerHttpRequest request, String storeId, Long fallbackStoreId) {
        if (StringUtils.hasText(storeId)) {
            return parsePositiveLong(storeId);
        }
        String storeIdHeader = request.getHeaders().getFirst(HttpHeaderNames.STORE_ID);
        if (StringUtils.hasText(storeIdHeader)) {
            return parsePositiveLong(storeIdHeader);
        }
        return fallbackStoreId;
    }

    private Long parsePositiveLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private HttpHeaders buildDownstreamHeaders(Long sellerId, Long storeId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaderNames.USER_ID, String.valueOf(sellerId));
        headers.set(HttpHeaderNames.STORE_ID, String.valueOf(storeId));
        if (StringUtils.hasText(securityProperties.getInternalAuthToken())
                && StringUtils.hasText(securityProperties.getInternalAuthHeader())) {
            headers.set(securityProperties.getInternalAuthHeader(), securityProperties.getInternalAuthToken());
        }
        return headers;
    }

    private ResponseEntity<JsonNode> badRequest(String message) {
        return ResponseEntity.badRequest().body(errorPayload(CODE_INVALID_REQUEST, message));
    }

    private ResponseEntity<JsonNode> badGateway(String message) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorPayload(CODE_DOWNSTREAM_ERROR, message));
    }

    private ObjectNode errorPayload(String code, String message) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("success", false);
        ObjectNode error = body.putObject("error");
        error.put("code", code);
        error.put("message", message);
        body.put("timestamp", Instant.now().toString());
        return body;
    }
}
