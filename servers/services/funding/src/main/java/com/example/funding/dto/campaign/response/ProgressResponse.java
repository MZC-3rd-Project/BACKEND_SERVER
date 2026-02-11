package com.example.funding.dto.campaign.response;

import com.example.core.id.jackson.SnowflakeId;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProgressResponse {

    @SnowflakeId
    private Long campaignId;

    private Long currentAmount;
    private Long goalAmount;
    private Integer currentQuantity;
    private Integer goalQuantity;
    private double progressRate;

    public static ProgressResponse of(Long campaignId, Long currentAmount, Long goalAmount,
                                       Integer currentQuantity, Integer goalQuantity) {
        double rate = goalAmount > 0
                ? Math.round((double) currentAmount / goalAmount * 10000) / 100.0
                : 0.0;

        return ProgressResponse.builder()
                .campaignId(campaignId)
                .currentAmount(currentAmount)
                .goalAmount(goalAmount)
                .currentQuantity(currentQuantity)
                .goalQuantity(goalQuantity)
                .progressRate(rate)
                .build();
    }
}
