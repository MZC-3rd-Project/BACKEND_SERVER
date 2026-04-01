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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Service
public class BusinessOrderQueryBffService {

    private static final String CODE_INVALID_REQUEST = "BFF-ORDER-400";
    private static final String CODE_DOWNSTREAM_ERROR = "BFF-ORDER-502";

    private final WebClient orderQueryWebClient;
    private final GatewaySessionPrincipalResolver sessionPrincipalResolver;
    private final BusinessGatewaySecurityProperties securityProperties;
    private final ObjectProvider<HmacSigner> hmacSignerProvider;
    private final ObjectMapper objectMapper;

    public BusinessOrderQueryBffService(
            WebClient.Builder webClientBuilder,
            GatewaySessionPrincipalResolver sessionPrincipalResolver,
            BusinessGatewaySecurityProperties securityProperties,
            ObjectProvider<HmacSigner> hmacSignerProvider,
            ObjectMapper objectMapper,
            @Value("${app.service.order-query-url:http://localhost:8073}") String orderQueryServiceUrl
    ) {
        this.orderQueryWebClient = webClientBuilder.baseUrl(orderQueryServiceUrl).build();
        this.sessionPrincipalResolver = sessionPrincipalResolver;
        this.securityProperties = securityProperties;
        this.hmacSignerProvider = hmacSignerProvider;
        this.objectMapper = objectMapper;
    }

    public Mono<ResponseEntity<JsonNode>> getMyOrders(String status) {
        return withAuthHeaders(headers ->
                orderQueryWebClient.method(HttpMethod.GET)
                        .uri(uriBuilder -> {
                            if (StringUtils.hasText(status)) {
                                return uriBuilder.path("/api/v1/order-query/my-orders")
                                        .queryParam("status", status)
                                        .build();
                            }
                            return uriBuilder.path("/api/v1/order-query/my-orders").build();
                        })
                        .headers(targetHeaders -> targetHeaders.addAll(headers))
                        .exchangeToMono(response -> response.bodyToMono(JsonNode.class)
                                .defaultIfEmpty(objectMapper.createObjectNode())
                                .map(payload -> ResponseEntity.status(response.statusCode()).body(payload)))
                        .onErrorResume(error -> {
                            log.warn("Business order list query failed. status={}", status, error);
                            return Mono.just(badGateway("주문 목록 조회에 실패했습니다."));
                        }));
    }

    public Mono<ResponseEntity<JsonNode>> getOrderDetail(Long orderId) {
        if (orderId == null || orderId <= 0) {
            return Mono.just(badRequest("orderId는 양수여야 합니다."));
        }

        return withAuthHeaders(headers ->
                orderQueryWebClient.method(HttpMethod.GET)
                        .uri("/api/v1/order-query/{orderId}", orderId)
                        .headers(targetHeaders -> targetHeaders.addAll(headers))
                        .exchangeToMono(response -> response.bodyToMono(JsonNode.class)
                                .defaultIfEmpty(objectMapper.createObjectNode())
                                .map(payload -> ResponseEntity.status(response.statusCode()).body(payload)))
                        .onErrorResume(error -> {
                            log.warn("Business order detail query failed. orderId={}", orderId, error);
                            return Mono.just(badGateway("주문 상세 조회에 실패했습니다."));
                        }));
    }

    private Mono<ResponseEntity<JsonNode>> withAuthHeaders(
            Function<HttpHeaders, Mono<ResponseEntity<JsonNode>>> callback) {
        return sessionPrincipalResolver.resolveFromSecurityContext()
                .switchIfEmpty(Mono.error(new SessionClaimParseException("요청 경로는 인증 정보가 필요합니다")))
                .flatMap(principal -> callback.apply(buildDownstreamHeaders(principal)))
                .onErrorResume(SessionClaimParseException.class,
                        error -> Mono.just(unauthorized("GW-AUTH-008", error.getMessage())));
    }

    private HttpHeaders buildDownstreamHeaders(GatewaySessionPrincipal principal) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaderNames.USER_ID, String.valueOf(principal.userId()));
        headers.set(HttpHeaderNames.USER_ROLES, principal.rolesHeaderValue());
        if (StringUtils.hasText(principal.sessionId())) {
            headers.set(HttpHeaderNames.SESSION_ID, principal.sessionId());
        }
        if (StringUtils.hasText(securityProperties.getInternalAuthToken())
                && StringUtils.hasText(securityProperties.getInternalAuthHeader())) {
            headers.set(securityProperties.getInternalAuthHeader(), securityProperties.getInternalAuthToken());
        }

        String gatewayContext = createSignedContextHeader(principal);
        if (StringUtils.hasText(gatewayContext)) {
            headers.set(HttpHeaderNames.GATEWAY_CONTEXT, gatewayContext);
        }
        return headers;
    }

    private String createSignedContextHeader(GatewaySessionPrincipal principal) {
        HmacSigner signer = hmacSignerProvider.getIfAvailable();
        if (signer == null || principal.userId() == null || principal.userId() <= 0) {
            return null;
        }

        String nonce = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();
        return GatewayContextHeaderCodec.encodeSigned(
                String.valueOf(principal.userId()),
                principal.rolesHeaderValue(),
                nonce,
                timestamp,
                signer
        );
    }

    private ResponseEntity<JsonNode> badRequest(String message) {
        return ResponseEntity.badRequest().body(errorPayload(CODE_INVALID_REQUEST, message));
    }

    private ResponseEntity<JsonNode> unauthorized(String code, String message) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorPayload(code, message));
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
