package com.example.chat.service.content;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ChatContentSanitizer {

    private static final int MAX_LENGTH = 4000;

    public String sanitize(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }

        String normalized = content
                .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "")
                .trim();

        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.length() > MAX_LENGTH) {
            return normalized.substring(0, MAX_LENGTH);
        }
        return normalized;
    }
}
