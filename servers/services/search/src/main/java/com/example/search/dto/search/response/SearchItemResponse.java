package com.example.search.dto.search.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SearchItemResponse {

    private final Long itemId;
    private final String title;
    private final String category;
    private final String domainType;
    private final Long price;
    private final Long effectivePrice;
    private final String status;
    private final String salesChannel;
    private final Integer channelPriority;
    private final Long activeHotDealId;
    private final Long activeCampaignId;
    private final Integer stock;
    private final Long thumbnailMediaId;
    private final String thumbnailUrl;

    private final Double score;
    private final String highlightedTitle;
    private final String highlightedDescription;
}
