package com.example.cart.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class StartCartCheckoutRequest {

    @NotBlank
    private String idempotencyKey;
}
