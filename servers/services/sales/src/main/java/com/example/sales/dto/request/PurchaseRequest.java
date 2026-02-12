package com.example.sales.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PurchaseRequest {

    @NotNull(message = "상품 ID는 필수입니다")
    private Long itemId;

    @NotNull(message = "재고 항목 ID는 필수입니다")
    private Long stockItemId;

    private Long referenceId;

    @NotNull(message = "수량은 필수입니다")
    @Min(value = 1, message = "수량은 1 이상이어야 합니다")
    private Integer quantity;
}
