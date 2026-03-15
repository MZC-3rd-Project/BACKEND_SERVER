package com.example.store.service.query.view;

import com.example.store.entity.AddressType;
import com.example.store.entity.StoreStatus;

public record StoreDetailBaseView(
    Long id,
    Long userId,
    String storeName,
    StoreStatus status,
    String description,
    String address,
    AddressType addressType
) {
}
