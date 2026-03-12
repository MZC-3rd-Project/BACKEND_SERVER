package com.example.clients.product.dto;

public record ProductQuoteLineItemRequest(
        Long itemId,
        String channelType,
        Long channelRefId,
        Long referenceId,
        Integer quantity
) {
}
