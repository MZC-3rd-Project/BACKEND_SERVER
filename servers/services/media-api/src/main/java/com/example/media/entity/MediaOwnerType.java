package com.example.media.entity;

import java.util.Locale;

public enum MediaOwnerType {
    USER_PROFILE,
    STORE,
    ITEM,
    POST,
    CHAT_MESSAGE,
    FUNDING,
    HOT_DEAL,
    COMMENT;

    public static MediaOwnerType fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        return MediaOwnerType.valueOf(normalized);
    }
}
