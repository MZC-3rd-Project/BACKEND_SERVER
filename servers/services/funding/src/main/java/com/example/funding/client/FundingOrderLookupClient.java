package com.example.funding.client;

import com.example.security.gateway.GatewaySecurityModuleProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class FundingOrderLookupClient {

    private final WebClient webClient;
    private final GatewaySecurityModuleProperties securityProperties;

    public FundingOrderLookupClient(
            WebClient.Builder webClientBuilder,
            GatewaySecurityModuleProperties securityProperties,
            @Value("${app.service.order-url:http://localhost:8090}") String orderServiceUrl
    ) {
        this.webClient = webClientBuilder.baseUrl(orderServiceUrl).build();
        this.securityProperties = securityProperties;
    }

    public FundingOrderSnapshot findOrder(Long orderId) {
        if (orderId == null || orderId <= 0L) {
            return null;
        }

        try {
            JsonNode response = webClient.get()
                    .uri("/internal/v1/orders/{orderId}", orderId)
                    .headers(this::applyInternalHeaders)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            JsonNode data = extractData(response);
            return data == null ? null : toSnapshot(data);
        } catch (WebClientResponseException.NotFound exception) {
            log.warn("Order snapshot not found. orderId={}", orderId);
            return null;
        } catch (Exception exception) {
            throw new IllegalStateException("Order snapshot lookup failed. orderId=" + orderId, exception);
        }
    }

    private void applyInternalHeaders(HttpHeaders headers) {
        if (StringUtils.hasText(securityProperties.getInternalAuthToken())) {
            headers.set(securityProperties.getInternalAuthHeader(), securityProperties.getInternalAuthToken());
        }
    }

    private JsonNode extractData(JsonNode response) {
        if (response == null || !response.path("success").asBoolean(false)) {
            return null;
        }
        JsonNode data = response.path("data");
        return data.isMissingNode() || data.isNull() ? null : data;
    }

    private FundingOrderSnapshot toSnapshot(JsonNode data) {
        Long orderId = positiveLong(data.path("orderId"));
        if (orderId == null) {
            return null;
        }

        List<FundingOrderSnapshot.LineItem> lineItems = new ArrayList<>();
        JsonNode itemsNode = data.path("items");
        if (itemsNode.isArray()) {
            for (JsonNode itemNode : itemsNode) {
                FundingOrderSnapshot.LineItem lineItem = toLineItem(itemNode);
                if (lineItem != null) {
                    lineItems.add(lineItem);
                }
            }
        }

        return new FundingOrderSnapshot(
                orderId,
                positiveLong(data.path("userId")),
                textOrNull(data.path("status")),
                lineItems
        );
    }

    private FundingOrderSnapshot.LineItem toLineItem(JsonNode itemNode) {
        Long itemId = positiveLong(itemNode.path("itemId"));
        Integer quantity = positiveInteger(itemNode.path("quantity"));
        Long lineAmount = positiveLong(itemNode.path("lineAmount"));

        if (itemId == null || quantity == null || lineAmount == null) {
            return null;
        }

        return new FundingOrderSnapshot.LineItem(
                textOrNull(itemNode.path("channelType")),
                positiveLong(itemNode.path("channelRefId")),
                itemId,
                quantity,
                lineAmount
        );
    }

    private Long positiveLong(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.canConvertToLong()) {
            long value = node.asLong();
            return value > 0L ? value : null;
        }
        String text = textOrNull(node);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            long value = Long.parseLong(text);
            return value > 0L ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer positiveInteger(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || !node.canConvertToInt()) {
            return null;
        }
        int value = node.asInt();
        return value > 0 ? value : null;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String value = node.asText(null);
        return StringUtils.hasText(value) ? value : null;
    }
}
