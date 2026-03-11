package com.example.clients.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCreateRequest(
        Long orderId,
        Long userId,
        LocalDateTime expiresAt,
        Long totalAmount,
        String recipientName,
        String recipientPhone,
        Long deliveryAddressId,
        String deliveryMemo,
        List<OrderCreateLineItem> lineItems
) {
}
