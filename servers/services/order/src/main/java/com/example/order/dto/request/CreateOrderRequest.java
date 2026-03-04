package com.example.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class CreateOrderRequest {

    @NotNull(message = "구매 ID는 필수입니다")
    private Long purchaseId;

    @NotNull(message = "사용자 ID는 필수입니다")
    private Long userId;

    @NotNull(message = "총 금액은 필수입니다")
    @Min(value = 0, message = "총 금액은 0 이상이어야 합니다")
    private Long totalAmount;

    private Long reservationId;

    @NotEmpty(message = "주문 항목은 최소 1개 이상이어야 합니다")
    @Valid
    private List<OrderItemRequest> items;

    @Getter
    @NoArgsConstructor
    public static class OrderItemRequest {

        @NotNull(message = "상품 ID는 필수입니다")
        private Long itemId;

        @NotNull(message = "상품명은 필수입니다")
        private String itemName;

        @NotNull(message = "수량은 필수입니다")
        @Min(value = 1, message = "수량은 1 이상이어야 합니다")
        private Integer quantity;

        @NotNull(message = "단가는 필수입니다")
        @Min(value = 0, message = "단가는 0 이상이어야 합니다")
        private Long unitPrice;
    }
}
