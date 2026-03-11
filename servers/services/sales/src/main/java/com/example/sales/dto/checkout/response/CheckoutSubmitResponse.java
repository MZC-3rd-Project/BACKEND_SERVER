package com.example.sales.dto.checkout.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.sales.entity.CheckoutSessionStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CheckoutSubmitResponse {

    @SnowflakeId
    private Long orderId;

    private CheckoutSessionStatus status;

    private LocalDateTime submittedAt;
}
