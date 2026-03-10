package com.example.clients.product.dto;

public record ProductQuotedLineItem(
        Long itemId,
        String itemType,
        String title,
        Long sellerId,
        Long storeId,
        Long referenceId,
        String referenceName,
        String stockItemType,
        Integer quantity,
        Long baseUnitPrice,
        Long finalUnitPrice,
        Long lineAmount
) {
}
