package com.example.search.document;

import org.springframework.util.StringUtils;

import java.util.Locale;

public enum SearchSalesChannel {
    HOT_DEAL(3),
    FUNDING(2),
    NORMAL(1);

    private final int priority;

    SearchSalesChannel(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }

    public static SearchSalesChannel fromStatus(String status) {
        if (!StringUtils.hasText(status)) {
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
