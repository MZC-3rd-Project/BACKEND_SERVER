package com.example.search.util;

import org.springframework.util.StringUtils;

import java.util.Locale;

public final class SearchKeywordNormalizer {

    private SearchKeywordNormalizer() {
    }

    public static String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }
}
