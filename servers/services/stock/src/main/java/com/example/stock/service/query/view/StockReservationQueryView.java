package com.example.stock.service.query.view;

import com.example.stock.entity.ReservationStatus;

import java.time.LocalDateTime;

public record StockReservationQueryView(
        Long stockItemId,
        Long userId,
        Long orderId,
        int quantity,
        ReservationStatus status,
        LocalDateTime expiredAt
) {
}
