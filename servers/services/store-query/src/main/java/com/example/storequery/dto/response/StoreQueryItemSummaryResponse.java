package com.example.storequery.dto.response;

import com.example.storequery.entity.StoreReadItem;
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

    public static StoreQueryItemSummaryResponse from(StoreReadItem item) {
        return StoreQueryItemSummaryResponse.builder()
            .itemId(item.getItemId())
            .sellerId(item.getSellerId())
            .title(item.getTitle())
            .price(item.getPrice())
            .itemType(item.getItemType())
            .status(item.getStatus())
            .thumbnailMediaId(item.getThumbnailMediaId())
            .thumbnailUrl(item.getThumbnailUrl())
            .build();
    }
}
