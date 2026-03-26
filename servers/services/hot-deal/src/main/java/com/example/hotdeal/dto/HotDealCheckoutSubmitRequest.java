package com.example.hotdeal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class HotDealCheckoutSubmitRequest {

    @NotNull(message = "주문 번호는 필수입니다")
    private Long orderId;

    @NotBlank(message = "수령인 이름은 필수입니다")
    private String recipientName;

    @NotBlank(message = "수령인 연락처는 필수입니다")
    private String recipientPhone;

    @NotNull(message = "배송지 ID는 필수입니다")
    private Long deliveryAddressId;

    private String deliveryMemo;
}
