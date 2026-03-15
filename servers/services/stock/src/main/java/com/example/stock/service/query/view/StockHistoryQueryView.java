package com.example.stock.service.query.view;

import com.example.stock.entity.ChangeType;

import java.time.LocalDateTime;

public record StockHistoryQueryView(
        Long id,
        Long stockItemId,
        ChangeType changeType,
        int quantity,
        String reason,
        Long reservationId,
        LocalDateTime createdAt
) {
}
