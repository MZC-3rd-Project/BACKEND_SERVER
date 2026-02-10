package com.example.product.dto.performance.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SeatGradeRequest {

    @NotBlank(message = "좌석 등급명은 필수입니다")
    private String gradeName;

    @NotNull(message = "가격은 필수입니다")
    @Min(value = 0, message = "가격은 0 이상이어야 합니다")
    private Long price;

    @Min(value = 1, message = "총 수량은 1 이상이어야 합니다")
    private int totalQuantity;

    @Min(value = 0, message = "펀딩 수량은 0 이상이어야 합니다")
    private int fundingQuantity;
}
