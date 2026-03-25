package com.example.gateway.bff.dto;

import java.util.Locale;

public enum BffItemType {
    PRODUCT("/api/products", "/api/seller/products"),
    GOODS("/api/goods", "/api/seller/goods"),
    PERFORMANCE("/api/performances", "/api/seller/performances");

    private final String collectionPath;
    private final String sellerCollectionPath;

    BffItemType(String collectionPath, String sellerCollectionPath) {
        this.collectionPath = collectionPath;
        this.sellerCollectionPath = sellerCollectionPath;
    }

    public String collectionPath() {
        return collectionPath;
    }

    public String sellerCollectionPath() {
        return sellerCollectionPath;
    }

    public static BffItemType fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return BffItemType.valueOf(normalized);
    }
}
