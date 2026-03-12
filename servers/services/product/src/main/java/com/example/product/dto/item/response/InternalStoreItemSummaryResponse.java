package com.example.product.dto.item.response;

import com.example.product.entity.item.Item;

import java.time.LocalDateTime;

public record InternalStoreItemSummaryResponse(
    Long itemId,
    Long storeId,
    Long sellerId,
    String title,
    Long price,
    String itemType,
    String status,
    Long thumbnailMediaId,
    LocalDateTime sourceUpdatedAt
) {

    public static InternalStoreItemSummaryResponse from(Item item) {
        return new InternalStoreItemSummaryResponse(
            item.getId(),
            item.getStoreId(),
            item.getSellerId(),
            item.getTitle(),
            item.getPrice(),
            item.getItemType() == null ? null : item.getItemType().name(),
            item.getStatus() == null ? null : item.getStatus().name(),
            item.getThumbnailMediaId(),
            item.getUpdatedAt()
        );
    }
}
