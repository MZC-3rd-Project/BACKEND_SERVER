package com.example.config.logging;

import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.util.UUID;

final class RequestIdSupport {

    static final String REQUEST_ID_KEY = "requestId";

    private RequestIdSupport() {
    }

    static String resolveRequestId(String candidate) {
        if (StringUtils.hasText(candidate)) {
            return candidate.trim();
        }
        return UUID.randomUUID().toString();
    }

    static void putRequestId(String requestId) {
        MDC.put(REQUEST_ID_KEY, requestId);
    }

    static void clearRequestId() {
        MDC.remove(REQUEST_ID_KEY);
    }
}
