package com.example.funding.dto.campaign.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class CampaignUpdateRequest {

    @NotNull(message = "목표 금액은 필수입니다")
    @Min(value = 1, message = "목표 금액은 1 이상이어야 합니다")
    private Long goalAmount;

    @Min(value = 1, message = "목표 수량은 1 이상이어야 합니다")
    private Integer goalQuantity;

    @Min(value = 1, message = "최소 참여 금액은 1 이상이어야 합니다")
    private Long minAmount;

    @NotNull(message = "시작 일시는 필수입니다")
    private LocalDateTime startAt;

    @NotNull(message = "종료 일시는 필수입니다")
    private LocalDateTime endAt;
}
