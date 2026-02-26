package com.example.clients.product.dto;

public record ProductItemSummary(
        Long itemId,
        Long sellerId,
        String title
) {
}
