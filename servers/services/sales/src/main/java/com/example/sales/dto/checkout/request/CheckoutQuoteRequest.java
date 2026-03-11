package com.example.sales.dto.checkout.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CheckoutQuoteRequest {

    @NotNull
    private Long orderId;
}
