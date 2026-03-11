package com.example.sales.dto.checkout.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CheckoutCancelRequest {

    @NotNull
    private Long orderId;
}
