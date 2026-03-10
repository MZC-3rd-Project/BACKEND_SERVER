package com.example.clients.stock.dto;

public record ReserveOrderStockLineItem(
        Long itemId,
        String stockItemType,
        Long referenceId,
        Integer quantity
) {
}
