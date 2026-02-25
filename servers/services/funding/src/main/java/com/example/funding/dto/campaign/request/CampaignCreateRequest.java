package com.example.funding.dto.campaign.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class CampaignCreateRequest {

    @NotNull(message = "상품 ID는 필수입니다")
    private Long itemId;

    @Size(max = 200, message = "제목은 200자 이하여야 합니다")
    private String title;

    @Size(max = 5000, message = "요약은 5000자 이하여야 합니다")
    private String summary;

    @Size(max = 120, message = "메이커명은 120자 이하여야 합니다")
    private String makerName;

    @Size(max = 100, message = "카테고리는 100자 이하여야 합니다")
    private String category;

    @Positive(message = "썸네일 미디어 ID는 양수여야 합니다")
    private Long thumbnailMediaId;

    @NotNull(message = "펀딩 유형은 필수입니다")
    private String fundingType;

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
