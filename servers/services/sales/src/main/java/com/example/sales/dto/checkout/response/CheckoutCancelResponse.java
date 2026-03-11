package com.example.sales.dto.checkout.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.sales.entity.CheckoutSessionStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CheckoutCancelResponse {

    @SnowflakeId
    private Long orderId;

    private CheckoutSessionStatus status;
}
