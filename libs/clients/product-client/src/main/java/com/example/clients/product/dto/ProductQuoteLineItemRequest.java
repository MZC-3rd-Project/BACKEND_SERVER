package com.example.clients.product.dto;

public record ProductQuoteLineItemRequest(
        Long itemId,
        Long referenceId,
        Integer quantity
) {
}
