package com.example.media.entity;

import java.util.Locale;

public enum MediaUsageType {
    PROFILE,
    THUMBNAIL,
    GALLERY,
    CONTENT,
    ATTACHMENT,
    VIDEO;

    public static MediaUsageType fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        return MediaUsageType.valueOf(normalized);
    }
}
