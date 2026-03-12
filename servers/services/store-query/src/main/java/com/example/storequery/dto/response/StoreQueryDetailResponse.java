package com.example.storequery.dto.response;

import com.example.storequery.entity.StoreQueryAddressType;
import com.example.storequery.entity.StoreQueryContactType;
import com.example.storequery.entity.StoreQueryStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record StoreQueryDetailResponse(
    Long storeId,
    Long userId,
    String storeName,
    StoreQueryStatus status,
    String description,
    String address,
    StoreQueryAddressType addressType,
    String contactValue,
    StoreQueryContactType contactType,
    String ownerNickname,
    String ownerProfileImageUrl,
    Integer activeItemCount,
    LocalDateTime latestItemUpdatedAt,
    StoreQueryImagesResponse images,
    List<StoreQueryItemSummaryResponse> items
) {
}
