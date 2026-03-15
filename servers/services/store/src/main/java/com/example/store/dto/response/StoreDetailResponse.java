package com.example.store.dto.response;

import com.example.store.dto.image.StoreImagesResponse;
import com.example.store.entity.AddressType;
import com.example.store.entity.StoreStatus;

import java.util.List;

public record StoreDetailResponse(
    Long id,
    Long userId,
    String storeName,
    StoreStatus status,
    String description,
    String address,
    AddressType addressType,
    // 이미지 리스트 thumnail. list gallery
    StoreImagesResponse image

) {
    public StoreDetailResponse(
        Long id,
        Long userId,
        String storeName,
        StoreStatus status,
        String description,
        String address,
        AddressType addressType
    ) {
        this(id, userId, storeName, status, description, address, addressType, null);
    }
}
