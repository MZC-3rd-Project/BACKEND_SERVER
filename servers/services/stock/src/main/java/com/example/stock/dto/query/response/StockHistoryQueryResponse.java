package com.example.stock.dto.query.response;

import com.example.stock.entity.ChangeType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class StockHistoryQueryResponse {

    private Long id;
    private Long stockItemId;
    private ChangeType changeType;
    private int quantity;
    private String reason;
    private Long reservationId;
    private LocalDateTime createdAt;
}
