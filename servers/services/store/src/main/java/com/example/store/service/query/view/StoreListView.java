package com.example.store.service.query.view;

import com.example.store.entity.ImageType;
import com.example.store.entity.StoreStatus;

public record StoreListView(
    Long id,
    Long userId,
    String storeName,
    StoreStatus status,
    String description,
    String address,
    String contactValue,
    Long thumbnailImageId,
    Long thumbnailMediaId,
    ImageType thumbnailImageType,
    Integer thumbnailSortOrder
) {
}
