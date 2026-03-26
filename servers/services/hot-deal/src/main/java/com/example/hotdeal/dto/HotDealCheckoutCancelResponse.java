package com.example.hotdeal.dto;

import com.example.core.id.jackson.SnowflakeId;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HotDealCheckoutCancelResponse {

    @SnowflakeId
    private Long orderId;
    private String status;
}
