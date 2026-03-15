package com.example.storequery.dto.response;

import com.example.storequery.entity.StoreQueryStatus;
import lombok.Builder;

@Builder
public record StoreQueryListResponse(
    Long storeId,
    Long userId,
    String storeName,
    StoreQueryStatus status,
    String description,
    String contactValue,
    String address,
    String ownerNickname,
    StoreQueryImageResponse thumbnail
) {
}
