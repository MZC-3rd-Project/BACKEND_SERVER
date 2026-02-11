package com.example.stock.dto.response;

import com.example.stock.entity.ChangeType;
import com.example.stock.entity.StockHistory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class StockHistoryResponse {

    private final Long id;
    private final Long stockItemId;
    private final ChangeType changeType;
    private final int quantity;
    private final String reason;
    private final Long reservationId;
    private final LocalDateTime createdAt;

    public static StockHistoryResponse from(StockHistory history) {
        return StockHistoryResponse.builder()
                .id(history.getId())
                .stockItemId(history.getStockItemId())
                .changeType(history.getChangeType())
                .quantity(history.getQuantity())
                .reason(history.getReason())
                .reservationId(history.getReservationId())
                .createdAt(history.getCreatedAt())
                .build();
    }
}
