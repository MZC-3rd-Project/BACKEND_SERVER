package com.example.store.service.query.view;

import com.example.store.entity.ImageType;

import java.time.LocalDateTime;

public record StoreImageView(
    Long imageId,
    Long storeId,
    Long mediaId,
    ImageType imageType,
    Integer sortOrder,
    LocalDateTime sourceUpdatedAt
) {
}
