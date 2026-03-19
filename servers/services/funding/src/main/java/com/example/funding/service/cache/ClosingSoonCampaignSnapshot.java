package com.example.funding.service.cache;

import com.example.funding.dto.campaign.response.CampaignResponse;
import com.example.funding.entity.FundingCampaign;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClosingSoonCampaignSnapshot {

    private Long id;
    private Long itemId;
    private Long sellerId;
    private String title;
    private String summary;
    private String makerName;
    private String category;
    private Long thumbnailMediaId;
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

    public static ClosingSoonCampaignSnapshot from(FundingCampaign campaign) {
        return ClosingSoonCampaignSnapshot.builder()
                .id(campaign.getId())
                .itemId(campaign.getItemId())
                .sellerId(campaign.getSellerId())
                .title(campaign.getTitle())
                .summary(campaign.getSummary())
                .makerName(campaign.getMakerName())
                .category(campaign.getCategory())
                .thumbnailMediaId(campaign.getThumbnailMediaId())
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

    public CampaignResponse toResponse() {
        return CampaignResponse.builder()
                .id(id)
                .itemId(itemId)
                .sellerId(sellerId)
                .title(title)
                .summary(summary)
                .makerName(makerName)
                .category(category)
                .thumbnailMediaId(thumbnailMediaId)
                .fundingType(fundingType)
                .goalAmount(goalAmount)
                .currentAmount(currentAmount)
                .goalQuantity(goalQuantity)
                .currentQuantity(currentQuantity)
                .minAmount(minAmount)
                .status(status)
                .startAt(startAt)
                .endAt(endAt)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .rewardOptions(List.of())
                .build();
    }
}
