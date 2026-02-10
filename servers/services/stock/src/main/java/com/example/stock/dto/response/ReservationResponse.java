package com.example.stock.dto.response;

import com.example.stock.entity.ReservationStatus;
import com.example.stock.entity.StockReservation;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReservationResponse {

    private Long id;
    private Long stockItemId;
    private Long userId;
    private int quantity;
    private ReservationStatus status;
    private LocalDateTime expiredAt;

    public static ReservationResponse from(StockReservation reservation) {
        return ReservationResponse.builder()
                .id(reservation.getId())
                .stockItemId(reservation.getStockItemId())
                .userId(reservation.getUserId())
                .quantity(reservation.getQuantity())
                .status(reservation.getStatus())
                .expiredAt(reservation.getExpiredAt())
                .build();
    }
}
