package com.example.search.client.dto;

public record StockSummary(
        Long itemId,
        Integer totalQuantity,
        Integer availableQuantity,
        Integer reservedQuantity,
        Integer soldQuantity,
        Boolean soldOut
) {
}
