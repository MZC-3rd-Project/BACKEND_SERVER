package com.example.search.service.query;

import java.util.Locale;

public enum SearchSortType {
    LATEST,
    POPULAR,
    PRICE_ASC,
    PRICE_DESC;

    public static SearchSortType fromNullable(String rawSort) {
        if (rawSort == null || rawSort.isBlank()) {
            return LATEST;
        }

        try {
            return SearchSortType.valueOf(rawSort.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("sort는 LATEST, POPULAR, PRICE_ASC, PRICE_DESC 중 하나여야 합니다");
        }
    }

    public boolean usesScore(boolean hasKeyword) {
        return this == POPULAR && hasKeyword;
    }
}
