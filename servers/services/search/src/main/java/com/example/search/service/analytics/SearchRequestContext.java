package com.example.search.service.analytics;

import org.springframework.util.StringUtils;

public record SearchRequestContext(
        Long userId,
        String sessionId,
        String journeyId,
        String correlationId,
        String causationId
) {

    public static SearchRequestContext of(
            Long userId,
            String sessionId,
            String journeyId,
            String correlationId,
            String causationId
    ) {
        return new SearchRequestContext(
                userId,
                trimToNull(sessionId),
                trimToNull(journeyId),
                trimToNull(correlationId),
                trimToNull(causationId)
        );
    }

    public SearchRequestContext overlay(
            String sessionId,
            String journeyId,
            String correlationId,
            String causationId
    ) {
        return SearchRequestContext.of(
                userId,
                firstNonBlank(sessionId, this.sessionId),
                firstNonBlank(journeyId, this.journeyId),
                firstNonBlank(correlationId, this.correlationId),
                firstNonBlank(causationId, this.causationId)
        );
    }

    private static String firstNonBlank(String primary, String fallback) {
        String normalizedPrimary = trimToNull(primary);
        if (normalizedPrimary != null) {
            return normalizedPrimary;
        }
        return trimToNull(fallback);
    }

    private static String trimToNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim();
    }
}
