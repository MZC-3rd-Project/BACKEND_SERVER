package com.example.storequery.projection;

import com.example.storequery.entity.StoreQueryImageType;

import java.time.LocalDateTime;

public record StoreReadImageSnapshot(
    StoreQueryImageType imageType,
    Long mediaId,
    String mediaUrl,
    Integer sortOrder,
    LocalDateTime sourceUpdatedAt
) {
}
