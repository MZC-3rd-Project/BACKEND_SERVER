package com.example.storequery.dto.response;

import lombok.Builder;

@Builder
public record StoreQueryItemSummaryResponse(
    Long itemId,
    Long sellerId,
    String title,
    Long price,
    String itemType,
    String status,
    Long thumbnailMediaId,
    String thumbnailUrl
) {
}
