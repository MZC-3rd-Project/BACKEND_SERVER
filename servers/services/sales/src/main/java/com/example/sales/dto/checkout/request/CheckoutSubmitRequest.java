package com.example.sales.dto.checkout.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CheckoutSubmitRequest {

    @NotNull
    private Long orderId;

    @NotBlank
    private String recipientName;

    @NotBlank
    private String recipientPhone;

    @NotNull
    private Long deliveryAddressId;

    private String deliveryMemo;
}
