package com.example.cart.service;

public record CartSnapshotData(
        Long storeId,
        String itemTitle,
        String thumbnailUrl,
        String storeName,
        Long displayPrice,
        String salesStatus
) {

    public static CartSnapshotData empty() {
        return new CartSnapshotData(null, null, null, null, null, null);
    }
}
