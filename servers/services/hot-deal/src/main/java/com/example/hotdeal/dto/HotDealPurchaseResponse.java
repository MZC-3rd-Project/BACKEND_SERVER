package com.example.hotdeal.dto;

import com.example.core.id.jackson.SnowflakeId;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class HotDealPurchaseResponse {

    private boolean success;

    @SnowflakeId
    private Long orderId;

    private LocalDateTime expiresAt;

    public static HotDealPurchaseResponse success(Long orderId, LocalDateTime expiresAt) {
        return HotDealPurchaseResponse.builder()
                .success(true)
                .orderId(orderId)
                .expiresAt(expiresAt)
                .build();
    }

    public static HotDealPurchaseResponse fail() {
        return HotDealPurchaseResponse.builder()
                .success(false)
                .build();
    }
}
