package com.example.sales.dto.payment.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentConfirmRequest {

    @NotNull
    private Long orderId;

    @NotNull
    private Long amount;
}
