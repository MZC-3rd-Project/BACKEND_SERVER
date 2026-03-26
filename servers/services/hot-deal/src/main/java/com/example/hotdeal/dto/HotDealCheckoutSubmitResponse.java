package com.example.hotdeal.dto;

import com.example.core.id.jackson.SnowflakeId;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HotDealCheckoutSubmitResponse {

    @SnowflakeId
    private Long orderId;
    private String status;
}
