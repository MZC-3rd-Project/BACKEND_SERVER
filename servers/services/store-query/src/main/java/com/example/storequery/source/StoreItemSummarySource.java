package com.example.storequery.source;

import java.time.LocalDateTime;

public record StoreItemSummarySource(
    Long itemId,
    Long storeId,
    Long sellerId,
    String title,
    Long price,
    String itemType,
    String status,
    Long thumbnailMediaId,
    String thumbnailUrl,
    LocalDateTime sourceUpdatedAt
) {
}
