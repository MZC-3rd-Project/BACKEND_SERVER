package com.example.sales.dto.payment.response;

import com.example.core.id.jackson.SnowflakeId;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PaymentConfirmResponse {

    @SnowflakeId
    private Long orderId;

    private Long paymentId;

    private Long amount;

    private String status;

    private LocalDateTime paidAt;
}
