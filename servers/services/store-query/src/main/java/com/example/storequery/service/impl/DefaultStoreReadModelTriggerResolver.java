package com.example.storequery.service.impl;

import com.example.storequery.service.StoreReadModelTriggerResolver;
import com.example.storequery.source.StoreOwnerStoreIdSourceReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultStoreReadModelTriggerResolver implements StoreReadModelTriggerResolver {

    private final ObjectMapper objectMapper;
    private final StoreOwnerStoreIdSourceReader storeOwnerStoreIdSourceReader;

    @Override
    public Set<Long> resolveStoreIds(String eventType, String aggregateType, String payloadJson) {
        if (!StringUtils.hasText(eventType) && !StringUtils.hasText(aggregateType)) {
            return Set.of();
        }

        JsonNode payload = readPayload(payloadJson);
        String normalizedEventType = normalize(eventType);
        String normalizedAggregateType = normalize(aggregateType);

        if (isStoreScoped(normalizedEventType, normalizedAggregateType)) {
            return toStoreIdSet(positiveLong(payload, "storeId"));
        }
        if (isUserScoped(normalizedEventType, normalizedAggregateType)) {
            Long userId = positiveLong(payload, "userId");
            if (userId == null) {
                return Set.of();
            }
            return new LinkedHashSet<>(storeOwnerStoreIdSourceReader.readStoreIdsByUserId(userId));
        }
        if (isItemScoped(normalizedEventType, normalizedAggregateType)) {
            return toStoreIdSet(positiveLong(payload, "storeId"));
        }

        return Set.of();
    }

    private JsonNode readPayload(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return objectMapper.createObjectNode();
        }

        try {
            return objectMapper.readTree(payloadJson);
        } catch (Exception exception) {
            log.warn("Failed to parse trigger payload. payload={}", payloadJson, exception);
            return objectMapper.createObjectNode();
        }
    }

    private Long positiveLong(JsonNode payload, String fieldName) {
        if (payload == null) {
            return null;
        }

        JsonNode field = payload.path(fieldName);
        if (field.isMissingNode() || field.isNull()) {
            return null;
        }

        long value = field.asLong(-1L);
        return value > 0L ? value : null;
    }

    private boolean isStoreScoped(String eventType, String aggregateType) {
        return eventType.startsWith("STORE") || "STORE".equals(aggregateType);
    }

    private boolean isUserScoped(String eventType, String aggregateType) {
        return eventType.startsWith("USER")
            || eventType.startsWith("PROFILE")
            || "USER".equals(aggregateType)
            || "PROFILE".equals(aggregateType);
    }

    private boolean isItemScoped(String eventType, String aggregateType) {
        return eventType.startsWith("ITEM") || "ITEM".equals(aggregateType);
    }

    private String normalize(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }

    private Set<Long> toStoreIdSet(Long storeId) {
        return storeId == null ? Set.of() : Set.of(storeId);
    }
}
