package com.example.funding.dto.campaign.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.funding.entity.FundingStatusHistory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class StatusHistoryResponse {

    @SnowflakeId
    private Long id;

    @SnowflakeId
    private Long campaignId;

    private String previousStatus;
    private String newStatus;
    private String reason;
    private LocalDateTime createdAt;

    public static StatusHistoryResponse from(FundingStatusHistory history) {
        return StatusHistoryResponse.builder()
                .id(history.getId())
                .campaignId(history.getCampaignId())
                .previousStatus(history.getPreviousStatus().name())
                .newStatus(history.getNewStatus().name())
                .reason(history.getReason())
                .createdAt(history.getCreatedAt())
                .build();
    }
}
