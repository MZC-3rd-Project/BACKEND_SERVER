package com.example.product.dto.goods.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ItemOptionRequest {

    @NotBlank(message = "옵션명은 필수입니다")
    private String optionName;

    @NotNull(message = "추가금액은 필수입니다")
    @Min(value = 0, message = "추가금액은 0 이상이어야 합니다")
    private Long additionalPrice;

    @Min(value = 0, message = "재고수량은 0 이상이어야 합니다")
    private int stockQuantity;
}
