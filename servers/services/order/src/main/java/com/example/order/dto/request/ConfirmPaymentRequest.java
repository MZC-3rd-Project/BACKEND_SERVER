package com.example.order.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ConfirmPaymentRequest {

    @NotNull(message = "결제 ID는 필수입니다")
    private Long paymentId;
}
