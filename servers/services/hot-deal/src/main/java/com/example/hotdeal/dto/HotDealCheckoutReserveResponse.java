package com.example.hotdeal.dto;

import com.example.core.id.jackson.SnowflakeId;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class HotDealCheckoutReserveResponse {

    @SnowflakeId
    private Long orderId;
    private LocalDateTime expiresAt;
}
