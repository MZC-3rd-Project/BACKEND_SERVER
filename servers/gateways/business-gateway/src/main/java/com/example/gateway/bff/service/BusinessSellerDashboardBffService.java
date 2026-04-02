package com.example.gateway.bff.service;

import com.example.contracts.http.HttpHeaderNames;
import com.example.gateway.config.BusinessGatewaySecurityProperties;
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
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class BusinessSellerDashboardBffService {

    private static final String CODE_INVALID_REQUEST = "BFF-DASHBOARD-400";
    private static final String CODE_DOWNSTREAM_ERROR = "BFF-DASHBOARD-502";

    private final WebClient analyticsDashboardWebClient;
    private final BusinessGatewaySecurityProperties securityProperties;
    private final GatewaySessionPrincipalResolver sessionPrincipalResolver;
    private final ObjectProvider<HmacSigner> hmacSignerProvider;
    private final ObjectMapper objectMapper;

    public BusinessSellerDashboardBffService(
            WebClient.Builder webClientBuilder,
            BusinessGatewaySecurityProperties securityProperties,
            GatewaySessionPrincipalResolver sessionPrincipalResolver,
            ObjectProvider<HmacSigner> hmacSignerProvider,
            ObjectMapper objectMapper,
            @Value("${app.service.analytics-dashboard-url:http://localhost:8095}") String analyticsDashboardServiceUrl
    ) {
        this.analyticsDashboardWebClient = webClientBuilder.baseUrl(analyticsDashboardServiceUrl).build();
        this.securityProperties = securityProperties;
        this.sessionPrincipalResolver = sessionPrincipalResolver;
        this.hmacSignerProvider = hmacSignerProvider;
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
        return sessionPrincipalResolver.resolveFromSecurityContext()
                .map(Optional::of)
                .onErrorResume(SessionClaimParseException.class, error -> {
                    log.info("[BusinessSellerDashboardBff] optional security context skipped. reason={}", error.getMessage());
                    return Mono.just(Optional.empty());
                })
                .defaultIfEmpty(Optional.empty())
                .flatMap(optionalPrincipal -> {
                    GatewaySessionPrincipal principal = optionalPrincipal.orElse(null);
                    Long sellerId = resolveSellerId(request, principal);
                    if (sellerId == null) {
                        return Mono.just(badRequest("X-User-Id 헤더가 없거나 유효하지 않습니다."));
                    }

                    Long resolvedStoreId = resolveStoreId(request, storeId, sellerId);
                    if (resolvedStoreId == null) {
                        return Mono.just(badRequest("storeId는 양수여야 합니다."));
                    }

                    HttpHeaders downstreamHeaders = buildDownstreamHeaders(
                            sellerId,
                            resolvedStoreId,
                            resolveRolesHeader(request, principal)
                    );

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
                            .onErrorResume(error -> {
                                log.warn("[BusinessSellerDashboardBff] overview query failed. sellerId={}, storeId={}",
                                        sellerId,
                                        resolvedStoreId,
                                        error);
                                return Mono.just(badGateway("셀러 대시보드 조회에 실패했습니다."));
                            });
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

    private Long resolveSellerId(ServerHttpRequest request, GatewaySessionPrincipal principal) {
        if (principal != null && principal.userId() != null && principal.userId() > 0) {
            return principal.userId();
        }
        return parsePositiveLong(request.getHeaders().getFirst(HttpHeaderNames.USER_ID));
    }

    private String resolveRolesHeader(ServerHttpRequest request, GatewaySessionPrincipal principal) {
        if (principal != null && StringUtils.hasText(principal.rolesHeaderValue())) {
            return principal.rolesHeaderValue();
        }
        return request.getHeaders().getFirst(HttpHeaderNames.USER_ROLES);
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
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private HttpHeaders buildDownstreamHeaders(Long sellerId, Long storeId, String rolesHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaderNames.USER_ID, String.valueOf(sellerId));
        headers.set(HttpHeaderNames.STORE_ID, String.valueOf(storeId));
        if (StringUtils.hasText(rolesHeader)) {
            headers.set(HttpHeaderNames.USER_ROLES, rolesHeader);
        }
        if (StringUtils.hasText(securityProperties.getInternalAuthToken())
                && StringUtils.hasText(securityProperties.getInternalAuthHeader())) {
            headers.set(securityProperties.getInternalAuthHeader(), securityProperties.getInternalAuthToken());
        }

        String gatewayContext = createSignedContextHeader(sellerId, rolesHeader);
        if (StringUtils.hasText(gatewayContext)) {
            headers.set(HttpHeaderNames.GATEWAY_CONTEXT, gatewayContext);
        }
        return headers;
    }

    private String createSignedContextHeader(Long sellerId, String rolesHeader) {
        if (sellerId == null || sellerId <= 0) {
            return null;
        }

        HmacSigner signer = hmacSignerProvider.getIfAvailable();
        if (signer == null) {
            return null;
        }

        String roles = StringUtils.hasText(rolesHeader) ? rolesHeader.trim() : "";
        String nonce = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();
        return GatewayContextHeaderCodec.encodeSigned(String.valueOf(sellerId), roles, nonce, timestamp, signer);
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
