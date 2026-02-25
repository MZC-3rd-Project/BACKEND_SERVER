package com.example.search.service.index;

import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

public final class SearchEventTimeParser {

    private SearchEventTimeParser() {
    }

    public static Long parseOccurredAtMillis(String occurredAt) {
        if (!StringUtils.hasText(occurredAt)) {
            return null;
        }

        String raw = occurredAt.trim();
        if (!StringUtils.hasText(raw)) {
            return null;
        }

        try {
            return Instant.parse(raw).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // fall through
        }

        try {
            return LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
