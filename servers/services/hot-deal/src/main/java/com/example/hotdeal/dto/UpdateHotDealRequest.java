package com.example.hotdeal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class UpdateHotDealRequest {

    @Min(value = 1, message = "할인율은 1% 이상이어야 합니다")
    @Max(value = 90, message = "할인율은 90% 이하여야 합니다")
    private Integer discountRate;

    @Min(value = 1, message = "최대 수량은 1 이상이어야 합니다")
    private Integer maxQuantity;

    @Min(value = 1, message = "1인당 최대 구매 수량은 1 이상이어야 합니다")
    private Integer maxPerUser;

    private LocalDateTime startAt;

    private LocalDateTime endAt;
}
