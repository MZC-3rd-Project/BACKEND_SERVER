package com.example.funding.dto.campaign.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.funding.entity.FundingCampaign;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CampaignResponse {

    @SnowflakeId
    private Long id;

    @SnowflakeId
    private Long itemId;

    @SnowflakeId
    private Long sellerId;

    private String fundingType;
    private Long goalAmount;
    private Long currentAmount;
    private Integer goalQuantity;
    private Integer currentQuantity;
    private Long minAmount;
    private String status;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CampaignResponse from(FundingCampaign campaign) {
        return CampaignResponse.builder()
                .id(campaign.getId())
                .itemId(campaign.getItemId())
                .sellerId(campaign.getSellerId())
                .fundingType(campaign.getFundingType().name())
                .goalAmount(campaign.getGoalAmount())
                .currentAmount(campaign.getCurrentAmount())
                .goalQuantity(campaign.getGoalQuantity())
                .currentQuantity(campaign.getCurrentQuantity())
                .minAmount(campaign.getMinAmount())
                .status(campaign.getStatus().name())
                .startAt(campaign.getStartAt())
                .endAt(campaign.getEndAt())
                .createdAt(campaign.getCreatedAt())
                .updatedAt(campaign.getUpdatedAt())
                .build();
    }
}
