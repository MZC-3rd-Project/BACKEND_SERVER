package com.example.funding.dto.campaign.response;

import com.example.core.id.jackson.SnowflakeId;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FundingRewardOptionResponse {

    @SnowflakeId
    private Long id;

    private String title;
    private Long price;
    private String shippingText;

    @SnowflakeId
    private Long itemOptionId;
}
