package com.example.storequery.source;

import com.example.storequery.entity.StoreQueryAddressType;
import com.example.storequery.entity.StoreQueryContactType;
import com.example.storequery.entity.StoreQueryStatus;
import com.example.storequery.projection.StoreReadImageSnapshot;

import java.time.LocalDateTime;
import java.util.List;

public record StoreSourceSnapshot(
    Long storeId,
    Long userId,
    String storeName,
    StoreQueryStatus status,
    String description,
    String defaultAddress,
    StoreQueryAddressType defaultAddressType,
    String primaryContactValue,
    StoreQueryContactType primaryContactType,
    List<StoreReadImageSnapshot> images,
    LocalDateTime sourceCreatedAt,
    LocalDateTime sourceUpdatedAt
) {
}
