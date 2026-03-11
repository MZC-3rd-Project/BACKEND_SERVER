package com.example.clients.stock.dto;

public record ReservedOrderStockLineItem(
        Long itemId,
        String stockItemType,
        Long referenceId,
        Integer quantity
) {
}
