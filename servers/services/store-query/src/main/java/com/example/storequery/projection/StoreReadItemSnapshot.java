package com.example.storequery.projection;

import java.time.LocalDateTime;

public record StoreReadItemSnapshot(
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
