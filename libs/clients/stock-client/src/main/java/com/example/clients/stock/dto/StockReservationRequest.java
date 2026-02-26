package com.example.clients.stock.dto;

public record StockReservationRequest(
        Long stockItemId,
        Long userId,
        int quantity,
        Long orderId
) {
}
