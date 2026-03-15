package com.example.store.service.query.view;

import com.example.store.entity.AddressType;
import com.example.store.entity.ContactType;
import com.example.store.entity.StoreStatus;

import java.time.LocalDateTime;

public record StoreSnapshotBaseView(
    Long storeId,
    Long userId,
    String storeName,
    StoreStatus status,
    String description,
    String address,
    AddressType addressType,
    String contactValue,
    ContactType contactType,
    LocalDateTime sourceCreatedAt,
    LocalDateTime sourceUpdatedAt
) {
}
