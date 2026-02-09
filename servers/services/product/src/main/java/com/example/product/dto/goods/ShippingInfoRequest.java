package com.example.product.dto.goods;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ShippingInfoRequest {

    @NotNull(message = "배송비는 필수입니다")
    @Min(value = 0, message = "배송비는 0 이상이어야 합니다")
    private Long shippingFee;

    private Long freeShippingThreshold;

    @Min(value = 1, message = "예상 배송일은 1일 이상이어야 합니다")
    private int estimatedDays;

    private String returnPolicy;
}
