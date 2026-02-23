package com.example.gateway.bff.dto;

import java.util.Locale;

public enum BffItemType {
    PRODUCT("/api/products"),
    GOODS("/api/goods"),
    PERFORMANCE("/api/performances");

    private final String collectionPath;

    BffItemType(String collectionPath) {
        this.collectionPath = collectionPath;
    }

    public String collectionPath() {
        return collectionPath;
    }

    public static BffItemType fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return BffItemType.valueOf(normalized);
    }
}
