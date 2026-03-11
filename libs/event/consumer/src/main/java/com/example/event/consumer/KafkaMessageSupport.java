package com.example.event.consumer;

import com.example.core.util.JsonDeserializationException;
import com.example.core.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;

public final class KafkaMessageSupport {

    private KafkaMessageSupport() {
    }

    public static String normalizePayload(Object rawMessage) {
        if (rawMessage == null) {
            return null;
        }
        String payload;
        if (rawMessage instanceof String text) {
            payload = text;
        } else if (rawMessage instanceof byte[] bytes) {
            payload = new String(bytes, StandardCharsets.UTF_8);
        } else {
            payload = JsonUtils.toJson(rawMessage);
        }
        return unwrapStructuredJsonString(payload);
    }

    public static JsonEventEnvelope readJsonEnvelope(String message) {
        JsonNode payload = readPayloadNode(message);
        return new JsonEventEnvelope(
                readText(payload, "eventId"),
                readText(payload, "eventType")
        );
    }

    private static JsonNode readPayloadNode(String message) {
        JsonNode payload = JsonUtils.fromJson(message, JsonNode.class);
        int depth = 0;
        while (payload != null && payload.isTextual() && depth++ < 3) {
            String nestedPayload = payload.asText();
            if (!looksLikeStructuredJson(nestedPayload)) {
                break;
            }
            payload = JsonUtils.fromJson(nestedPayload, JsonNode.class);
        }
        return payload;
    }

    private static String unwrapStructuredJsonString(String payload) {
        String normalized = payload;
        int depth = 0;
        while (depth++ < 3) {
            JsonNode node;
            try {
                node = JsonUtils.fromJson(normalized, JsonNode.class);
            } catch (JsonDeserializationException exception) {
                return normalized;
            }
            if (!node.isTextual()) {
                return normalized;
            }

            String nestedPayload = node.asText();
            if (!looksLikeStructuredJson(nestedPayload)) {
                return normalized;
            }
            normalized = nestedPayload;
        }
        return normalized;
    }

    private static String readText(JsonNode payload, String field) {
        if (payload == null || !payload.hasNonNull(field)) {
            return null;
        }
        return payload.path(field).asText(null);
    }

    private static boolean looksLikeStructuredJson(String payload) {
        if (payload == null) {
            return false;
        }
        String trimmed = payload.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }
}
