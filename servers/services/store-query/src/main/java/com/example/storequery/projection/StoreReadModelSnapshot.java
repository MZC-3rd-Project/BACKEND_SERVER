package com.example.storequery.projection;

import com.example.storequery.entity.StoreQueryAddressType;
import com.example.storequery.entity.StoreQueryContactType;
import com.example.storequery.entity.StoreQueryStatus;

import java.time.LocalDateTime;
import java.util.List;

public record StoreReadModelSnapshot(
    Long storeId,
    Long userId,
    String storeName,
    String ownerNickname,
    String ownerProfileImageUrl,
    StoreQueryStatus status,
    String description,
    String defaultAddress,
    StoreQueryAddressType defaultAddressType,
    String primaryContactValue,
    StoreQueryContactType primaryContactType,
    Long thumbnailMediaId,
    String thumbnailUrl,
    Integer thumbnailSortOrder,
    Integer galleryCount,
    Integer activeItemCount,
    LocalDateTime latestItemUpdatedAt,
    String searchText,
    LocalDateTime sourceCreatedAt,
    LocalDateTime sourceUpdatedAt,
    List<StoreReadImageSnapshot> images,
    List<StoreReadItemSnapshot> items
) {
}
