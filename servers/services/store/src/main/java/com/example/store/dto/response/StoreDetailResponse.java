package com.example.store.dto.response;

import com.example.store.entity.AddressType;
import com.example.store.entity.StoreStatus;

public record StoreDetailResponse(
    Long id,
    Long userId,
    String storeName,
    StoreStatus status,
    String description,
    String address,
    AddressType addressType
    // 이미지 리스트 thumnail. list gallery
) {
    public static StoreDetailResponse of(StoreDetailResponse response){
        return new StoreDetailResponse(
            response.id(),
            response.userId(),
            response.storeName(),
            response.status(),
            response.description(),
            response.address(),
            response.addressType()
        );
    }
}
