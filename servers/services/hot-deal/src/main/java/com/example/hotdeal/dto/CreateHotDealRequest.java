package com.example.hotdeal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class CreateHotDealRequest {

    @NotNull(message = "상품 ID는 필수입니다")
    private Long itemId;

    @NotNull(message = "할인율은 필수입니다")
    @Min(value = 1, message = "할인율은 1% 이상이어야 합니다")
    @Max(value = 90, message = "할인율은 90% 이하여야 합니다")
    private Integer discountRate;

    @NotNull(message = "최대 수량은 필수입니다")
    @Min(value = 1, message = "최대 수량은 1 이상이어야 합니다")
    private Integer maxQuantity;

    private LocalDateTime startAt;

    private LocalDateTime endAt;
}
