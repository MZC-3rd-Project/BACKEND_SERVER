package com.example.hotdeal.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class HotDealCheckoutReserveRequest {

    @NotNull(message = "수량은 필수입니다")
    @Min(value = 1, message = "수량은 1 이상이어야 합니다")
    private Integer quantity;

    @NotBlank(message = "대기열 토큰은 필수입니다")
    private String token;

    @NotBlank(message = "멱등 키는 필수입니다")
    private String idempotencyKey;
}
