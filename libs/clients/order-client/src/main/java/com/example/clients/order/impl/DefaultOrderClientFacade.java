package com.example.clients.order.impl;

import com.example.clients.order.dto.OrderCreateLineItem;
import com.example.clients.order.dto.OrderCreateRequest;
import com.example.clients.order.dto.OrderCreateResponse;
import com.example.clients.order.exception.OrderClientConflictException;
import com.example.clients.order.exception.OrderClientException;
import com.example.clients.order.exception.OrderClientRetriableException;
import com.example.clients.order.exception.OrderClientTerminalException;
import com.example.clients.order.exception.OrderClientValidationException;
import com.example.clients.order.facade.OrderCreateClientFacade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.util.List;

public class DefaultOrderClientFacade implements OrderCreateClientFacade {

    private static final String CREATE_ORDER_PATH = "/internal/v1/orders";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public DefaultOrderClientFacade(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            String orderServiceUrl
    ) {
        this.webClient = webClientBuilder.baseUrl(orderServiceUrl).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public OrderCreateResponse createOrder(OrderCreateRequest request) {
        validate(request);

        try {
            OrderApiCallResult result = webClient.post()
                    .uri(CREATE_ORDER_PATH)
                    .bodyValue(request)
                    .exchangeToMono(clientResponse -> clientResponse.bodyToMono(JsonNode.class)
                            .defaultIfEmpty(MissingNode.getInstance())
                            .map(body -> new OrderApiCallResult(clientResponse.statusCode().value(), body)))
                    .block();

            if (result == null) {
                throw new OrderClientRetriableException("order create returned no response", null);
            }
            return mapResponse(result);
        } catch (WebClientRequestException e) {
            throw new OrderClientRetriableException("order create request failed", e);
        } catch (OrderClientException e) {
            throw e;
        } catch (Exception e) {
            throw new OrderClientRetriableException("order create failed", e);
        }
    }

    private OrderCreateResponse mapResponse(OrderApiCallResult result) throws Exception {
        OrderApiEnvelope envelope = toEnvelope(result.body());
        int status = result.statusCode();

        if (status >= 500) {
            throw new OrderClientRetriableException(
                    buildErrorMessage("order create failed with server error", envelope.error()),
                    errorCode(envelope.error()),
                    null
            );
        }

        if (status >= 400) {
            throw mapClientError(status, envelope.error());
        }

        if (!envelope.success()) {
            throw new OrderClientTerminalException(
                    buildErrorMessage("order create returned unsuccessful response", envelope.error()),
                    errorCode(envelope.error()),
                    null
            );
        }

        if (envelope.data() == null || envelope.data().isMissingNode() || envelope.data().isNull()) {
            throw new OrderClientRetriableException("order create returned empty data", null);
        }

        return objectMapper.treeToValue(envelope.data(), OrderCreateResponse.class);
    }

    private OrderClientException mapClientError(int status, OrderApiError error) {
        String message = buildErrorMessage("order create client error", error);
        String errorCode = errorCode(error);

        if (status == 400 || status == 422) {
            return new OrderClientValidationException(message, errorCode, null);
        }
        if (status == 409) {
            return new OrderClientConflictException(message, errorCode, null);
        }
        return new OrderClientTerminalException(message, errorCode, null);
    }

    private OrderApiEnvelope toEnvelope(JsonNode body) {
        JsonNode effectiveBody = body == null ? MissingNode.getInstance() : body;
        JsonNode errorNode = effectiveBody.path("error");
        return new OrderApiEnvelope(
                effectiveBody.path("success").asBoolean(false),
                effectiveBody.path("data"),
                new OrderApiError(
                        nullableText(errorNode.path("code")),
                        nullableText(errorNode.path("message"))
                )
        );
    }

    private String buildErrorMessage(String fallback, OrderApiError error) {
        if (error == null) {
            return fallback;
        }
        if (StringUtils.hasText(error.message())) {
            return fallback + ": " + error.message();
        }
        if (StringUtils.hasText(error.code())) {
            return fallback + ": " + error.code();
        }
        return fallback;
    }

    private String errorCode(OrderApiError error) {
        return error == null ? null : error.code();
    }

    private void validate(OrderCreateRequest request) {
        if (request == null) {
            throw new OrderClientValidationException("order create request is invalid: request");
        }
        if (request.orderId() == null || request.orderId() <= 0) {
            throw new OrderClientValidationException("order create request is invalid: orderId");
        }
        if (request.userId() == null || request.userId() <= 0) {
            throw new OrderClientValidationException("order create request is invalid: userId");
        }
        if (request.expiresAt() == null) {
            throw new OrderClientValidationException("order create request is invalid: expiresAt");
        }
        if (request.totalAmount() == null || request.totalAmount() < 0) {
            throw new OrderClientValidationException("order create request is invalid: totalAmount");
        }
        if (!StringUtils.hasText(request.recipientName())) {
            throw new OrderClientValidationException("order create request is invalid: recipientName");
        }
        if (!StringUtils.hasText(request.recipientPhone())) {
            throw new OrderClientValidationException("order create request is invalid: recipientPhone");
        }
        if (request.deliveryAddressId() == null || request.deliveryAddressId() <= 0) {
            throw new OrderClientValidationException("order create request is invalid: deliveryAddressId");
        }

        List<OrderCreateLineItem> lineItems = request.lineItems();
        if (lineItems == null || lineItems.isEmpty()) {
            throw new OrderClientValidationException("order create request is invalid: lineItems");
        }

        for (int index = 0; index < lineItems.size(); index++) {
            validateLineItem(lineItems.get(index), index);
        }
    }

    private void validateLineItem(OrderCreateLineItem lineItem, int index) {
        String prefix = "order create request is invalid: lineItems[" + index + "]";
        if (lineItem == null) {
            throw new OrderClientValidationException(prefix);
        }
        if (!StringUtils.hasText(lineItem.channelType())) {
            throw new OrderClientValidationException(prefix + ".channelType");
        }
        if (lineItem.itemId() == null || lineItem.itemId() <= 0) {
            throw new OrderClientValidationException(prefix + ".itemId");
        }
        if (lineItem.quantity() == null || lineItem.quantity() <= 0) {
            throw new OrderClientValidationException(prefix + ".quantity");
        }
        if (lineItem.storeId() == null || lineItem.storeId() <= 0) {
            throw new OrderClientValidationException(prefix + ".storeId");
        }
        if (lineItem.finalUnitPrice() == null || lineItem.finalUnitPrice() < 0) {
            throw new OrderClientValidationException(prefix + ".finalUnitPrice");
        }
        if (lineItem.lineAmount() == null || lineItem.lineAmount() < 0) {
            throw new OrderClientValidationException(prefix + ".lineAmount");
        }
    }

    private String nullableText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return StringUtils.hasText(value) ? value : null;
    }

    private record OrderApiCallResult(int statusCode, JsonNode body) {
    }

    private record OrderApiEnvelope(boolean success, JsonNode data, OrderApiError error) {
    }

    private record OrderApiError(String code, String message) {
    }
}
