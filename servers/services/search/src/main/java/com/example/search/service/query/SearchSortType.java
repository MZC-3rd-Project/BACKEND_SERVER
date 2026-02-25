package com.example.search.service.query;

import com.example.core.exception.BusinessException;
import com.example.search.exception.SearchErrorCode;

import java.util.Locale;

public enum SearchSortType {
    RELEVANCE,
    LATEST,
    POPULAR,
    PRICE_ASC,
    PRICE_DESC;

    public static SearchSortType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return LATEST;
        }
        try {
            return SearchSortType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(SearchErrorCode.INVALID_SEARCH_PARAMETER,
                    "지원하지 않는 정렬 타입입니다: " + raw);
        }
    }
}
