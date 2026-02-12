package com.example.hotdeal.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class HotDealPurchaseResponse {

    private boolean success;
    private String reservationId;
    private LocalDateTime expiresAt;

    public static HotDealPurchaseResponse success(String reservationId, LocalDateTime expiresAt) {
        return HotDealPurchaseResponse.builder()
                .success(true)
                .reservationId(reservationId)
                .expiresAt(expiresAt)
                .build();
    }

    public static HotDealPurchaseResponse fail() {
        return HotDealPurchaseResponse.builder()
                .success(false)
                .build();
    }
}
