package com.example.gateway.bff.dto.catalog;

import java.util.Locale;

public enum CatalogSalesChannel {
    ALL,
    HOT_DEAL,
    FUNDING,
    NORMAL;

    public static CatalogSalesChannel fromNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return ALL;
        }
        try {
            return CatalogSalesChannel.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("channel은 ALL, HOT_DEAL, FUNDING, NORMAL 중 하나여야 합니다");
        }
    }

    public static CatalogSalesChannel fromStatus(String status) {
        if (status == null || status.isBlank()) {
            return NORMAL;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "HOT_DEAL" -> HOT_DEAL;
            case "FUNDING", "FUNDED", "FUND_FAILED" -> FUNDING;
            default -> NORMAL;
        };
    }
}
