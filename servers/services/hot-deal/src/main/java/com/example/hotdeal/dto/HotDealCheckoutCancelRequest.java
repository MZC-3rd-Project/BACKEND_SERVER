package com.example.hotdeal.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class HotDealCheckoutCancelRequest {

    @NotNull(message = "주문 번호는 필수입니다")
    private Long orderId;
}
