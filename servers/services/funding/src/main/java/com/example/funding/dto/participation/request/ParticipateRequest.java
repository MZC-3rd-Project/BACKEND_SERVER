package com.example.funding.dto.participation.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ParticipateRequest {

    @NotNull(message = "참여 금액은 필수입니다")
    @Min(value = 1, message = "참여 금액은 1 이상이어야 합니다")
    private Long amount;

    @Min(value = 1, message = "수량은 1 이상이어야 합니다")
    private Integer quantity;

    private Long seatGradeId;

    private Long itemOptionId;
}
