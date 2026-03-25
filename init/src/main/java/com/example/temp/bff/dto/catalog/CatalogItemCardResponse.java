package com.example.gateway.bff.dto.catalog;

import com.example.gateway.bff.dto.BffItemType;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CatalogItemCardResponse(
        Long itemId,
        String title,
        BffItemType itemType,
        CatalogSalesChannel salesChannel,
        String status,
        CatalogPriceResponse price,
        Integer stock,
        Integer availableStock,
        Long thumbnailMediaId,
        String thumbnailUrl,
        Long activeHotDealId,
        Long activeCampaignId,
        CatalogDetailTargetResponse detailTarget
) {
}
