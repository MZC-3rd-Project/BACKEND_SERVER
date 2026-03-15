package com.example.stock.dto.query.response;

import com.example.stock.entity.ReservationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class StockReservationQueryResponse {

    private Long stockItemId;
    private Long userId;
    private Long orderId;
    private int quantity;
    private ReservationStatus status;
    private LocalDateTime expiredAt;
}
