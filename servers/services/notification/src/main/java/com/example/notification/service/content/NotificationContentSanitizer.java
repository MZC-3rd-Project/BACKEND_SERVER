package com.example.notification.service.content;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class NotificationContentSanitizer {

    public String sanitizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = stripUnsafeControlChars(value);
        return HtmlUtils.htmlEscape(normalized);
    }

    public Map<String, Object> sanitizeVariables(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> sanitized = new LinkedHashMap<>();
        variables.forEach((key, value) -> sanitized.put(key, sanitizeValue(value)));
        return sanitized;
    }

    private Object sanitizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return sanitizeText(text);
        }
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> nested = new LinkedHashMap<>();
            mapValue.forEach((key, nestedValue) -> nested.put(String.valueOf(key), sanitizeValue(nestedValue)));
            return nested;
        }
        if (value instanceof List<?> listValue) {
            return listValue.stream().map(this::sanitizeValue).toList();
        }
        return value;
    }

    private String stripUnsafeControlChars(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\n' || ch == '\r' || ch == '\t' || ch >= 0x20) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }
}
