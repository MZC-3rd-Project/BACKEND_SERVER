package com.example.store.dto.response;

import com.example.store.entity.StoreStatus;

import java.util.List;

public record StoreListResponse(
    Long userId,
    String storeName,
    StoreStatus status,
    String description,
    String contactValue,
    String address
) {
}
