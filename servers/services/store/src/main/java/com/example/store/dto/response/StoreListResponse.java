package com.example.store.dto.response;

import com.example.store.dto.image.StoreImageResponse;
import com.example.store.entity.StoreStatus;

public record StoreListResponse(
    Long id,
    Long userId,
    String storeName,
    StoreStatus status,
    String description,
    String contactValue,
    String address,
    // 이미지 thumnail 1
    StoreImageResponse isThumbnail
) {
}
