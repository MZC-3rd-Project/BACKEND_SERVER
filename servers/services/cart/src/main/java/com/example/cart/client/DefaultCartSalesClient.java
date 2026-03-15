package com.example.cart.client;

import com.example.cart.domain.CartLine;
import com.example.cart.dto.response.CartCheckoutReservationResponse;
import com.example.cart.exception.CartErrorCode;
import com.example.contracts.http.HttpHeaderNames;
import com.example.core.exception.BusinessException;
import com.example.core.exception.TechnicalException;
import com.example.security.gateway.GatewayContextHeaderCodec;
import com.example.security.gateway.GatewaySecurityModuleProperties;
import com.example.security.signature.HmacSigner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
@RequiredArgsConstructor
public class DefaultCartSalesClient implements CartSalesClient {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final GatewaySecurityModuleProperties gatewaySecurityModuleProperties;
    private final ObjectProvider<HmacSigner> hmacSignerProvider;

    @Value("${app.service.sales-url:http://localhost:8087}")
    private String salesServiceUrl;

    @Override
    public CartCheckoutReservationResponse reserve(Long userId, String idempotencyKey, List<CartLine> lines) {
        WebClient webClient = webClientBuilder.baseUrl(salesServiceUrl).build();
        SalesReserveRequest request = new SalesReserveRequest(
                lines.stream()
                        .map(line -> new SalesReserveLineItem(
                                line.getIdentity().itemId(),
                                line.getIdentity().channelType(),
                                line.getIdentity().channelRefId(),
                                line.getStockItemType(),
                                line.getIdentity().referenceId(),
                                line.getQuantity()
                        ))
                        .toList(),
                idempotencyKey
        );

        try {
            JsonNode response = webClient.post()
                    .uri("/api/v1/sales/checkout/reservations")
                    .headers(headers -> applyGatewayHeaders(headers, userId))
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.path("success").asBoolean()) {
                throw new TechnicalException(CartErrorCode.CHECKOUT_FAILED, "sales reserve returned invalid response");
            }

            JsonNode data = response.path("data");
            if (data.isMissingNode() || data.isNull()) {
                throw new TechnicalException(CartErrorCode.CHECKOUT_FAILED, "sales reserve returned empty data");
            }
            return objectMapper.treeToValue(data, CartCheckoutReservationResponse.class);
        } catch (WebClientResponseException e) {
            String message = extractMessage(e);
            if (e.getStatusCode().is4xxClientError()) {
                throw new BusinessException(resolve4xxError(e.getStatusCode()), message, e);
            }
            throw new TechnicalException(CartErrorCode.CHECKOUT_FAILED, message, e);
        } catch (BusinessException | TechnicalException e) {
            throw e;
        } catch (Exception e) {
            throw new TechnicalException(CartErrorCode.CHECKOUT_FAILED, "sales reserve call failed", e);
        }
    }

    private void applyGatewayHeaders(HttpHeaders headers, Long userId) {
        if (StringUtils.hasText(gatewaySecurityModuleProperties.getInternalAuthToken())) {
            headers.set(
                    gatewaySecurityModuleProperties.getInternalAuthHeader(),
                    gatewaySecurityModuleProperties.getInternalAuthToken()
            );
        }

        HmacSigner signer = hmacSignerProvider.getIfAvailable();
        if (signer != null) {
            long timestamp = Instant.now().toEpochMilli();
            String token = GatewayContextHeaderCodec.encodeSigned(
                    String.valueOf(userId),
                    "",
                    UUID.randomUUID().toString(),
                    timestamp,
                    signer
            );
            headers.set(HttpHeaderNames.GATEWAY_CONTEXT, token);
            return;
        }

        headers.set(gatewaySecurityModuleProperties.getUserIdHeader(), String.valueOf(userId));
    }

    private CartErrorCode resolve4xxError(HttpStatusCode status) {
        return status.value() == HttpStatus.CONFLICT.value() ? CartErrorCode.CHECKOUT_REJECTED : CartErrorCode.INVALID_CART_LINE;
    }

    private String extractMessage(WebClientResponseException e) {
        try {
            JsonNode body = objectMapper.readTree(e.getResponseBodyAsString());
            String message = body.path("error").path("message").asText(null);
            if (StringUtils.hasText(message)) {
                return message;
            }
        } catch (Exception ignored) {
        }
        return "sales reserve failed: " + e.getStatusCode().value();
    }

    private record SalesReserveRequest(
            List<SalesReserveLineItem> lineItems,
            String idempotencyKey
    ) {
    }

    private record SalesReserveLineItem(
            Long itemId,
            String channelType,
            Long channelRefId,
            String stockItemType,
            Long referenceId,
            Integer quantity
    ) {
    }
}
