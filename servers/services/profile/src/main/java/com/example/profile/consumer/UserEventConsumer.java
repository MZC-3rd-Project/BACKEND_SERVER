package com.example.profile.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.util.JsonDeserializationException;
import com.example.core.util.JsonUtils;
import com.example.profile.service.command.ProfileProjectionSyncService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventConsumer {

    private static final String IDEMPOTENT_EVENT_TYPE = "USER_EVENT";
    private static final String USER_CREATED_EVENT_TYPE = "USER_CREATED";
    private static final String USER_EMAIL_CHANGED_EVENT_TYPE = "USER_EMAIL_CHANGED";
    private static final String USER_WITHDRAWN_EVENT_TYPE = "USER_WITHDRAWN";

    private final IdempotentConsumerService idempotentConsumerService;
    private final ProfileProjectionSyncService profileProjectionSyncService;

    @KafkaListener(topics = "user-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consume(String message) {
        JsonNode payload = parsePayload(message);
        if (payload == null) {
            return;
        }

        String eventId = readText(payload, "eventId");
        String rawEventType = readText(payload, "eventType");
        if (!hasRequiredMetadata(eventId, rawEventType)) {
            return;
        }

        String normalizedEventType = normalizeEventType(rawEventType);
        if (!isSupportedType(normalizedEventType)) {
            log.debug("[ProfileUserEventConsumer] ignore unsupported type. eventId={}, eventType={}", eventId, rawEventType);
            return;
        }

        switch (normalizedEventType) {
            case USER_CREATED_EVENT_TYPE -> consumeUserCreated(message, eventId, rawEventType);
            case USER_EMAIL_CHANGED_EVENT_TYPE -> consumeUserEmailChanged(message, eventId, rawEventType);
            case USER_WITHDRAWN_EVENT_TYPE -> consumeUserWithdrawn(message, eventId, rawEventType);
            default -> throw new IllegalArgumentException("Unsupported event type: " + normalizedEventType);
        }
    }

    private void consumeUserCreated(String message, String eventId, String rawEventType) {
        UserCreatedEventDto event = parseEvent(message, UserCreatedEventDto.class, rawEventType);
        if (event == null || event.userId() == null
                || !StringUtils.hasText(event.email())
                || !StringUtils.hasText(event.nickname())) {
            log.warn("[ProfileUserEventConsumer] skip invalid payload. eventId={}, eventType={}, userId={}",
                    eventId, rawEventType, event == null ? null : event.userId());
            return;
        }

        executeIdempotent(eventId, rawEventType, event.userId(),
                () -> profileProjectionSyncService.upsertFromUserCreated(event.userId(), event.email(), event.nickname()));
    }

    private void consumeUserEmailChanged(String message, String eventId, String rawEventType) {
        UserEmailChangedEventDto event = parseEvent(message, UserEmailChangedEventDto.class, rawEventType);
        if (event == null || event.userId() == null || !StringUtils.hasText(event.newEmail())) {
            log.warn("[ProfileUserEventConsumer] skip invalid payload. eventId={}, eventType={}, userId={}",
                    eventId, rawEventType, event == null ? null : event.userId());
            return;
        }

        executeIdempotent(eventId, rawEventType, event.userId(),
                () -> profileProjectionSyncService.applyUserEmailChanged(event.userId(), event.newEmail()));
    }

    private void consumeUserWithdrawn(String message, String eventId, String rawEventType) {
        UserWithdrawnEventDto event = parseEvent(message, UserWithdrawnEventDto.class, rawEventType);
        if (event == null || event.userId() == null) {
            log.warn("[ProfileUserEventConsumer] skip invalid payload. eventId={}, eventType={}, userId={}",
                    eventId, rawEventType, event == null ? null : event.userId());
            return;
        }

        executeIdempotent(eventId, rawEventType, event.userId(),
                () -> profileProjectionSyncService.withdrawProjection(event.userId()));
    }

    private JsonNode parsePayload(String message) {
        try {
            return JsonUtils.fromJson(message, JsonNode.class);
        } catch (JsonDeserializationException e) {
            log.warn("[ProfileUserEventConsumer] skip malformed message. payload={}", message);
            return null;
        }
    }

    private <T> T parseEvent(String message, Class<T> clazz, String eventType) {
        try {
            return JsonUtils.fromJson(message, clazz);
        } catch (JsonDeserializationException e) {
            log.warn("[ProfileUserEventConsumer] payload parse failed. eventType={}, payload={}", eventType, message);
            return null;
        }
    }

    private boolean hasRequiredMetadata(String eventId, String eventType) {
        if (!StringUtils.hasText(eventId) || !StringUtils.hasText(eventType)) {
            log.warn("[ProfileUserEventConsumer] skip invalid metadata. eventId={}, eventType={}", eventId, eventType);
            return false;
        }
        return true;
    }

    private String readText(JsonNode payload, String field) {
        if (payload == null || !payload.hasNonNull(field)) {
            return null;
        }
        return payload.path(field).asText(null);
    }

    private boolean isSupportedType(String normalizedEventType) {
        return USER_CREATED_EVENT_TYPE.equals(normalizedEventType)
                || USER_EMAIL_CHANGED_EVENT_TYPE.equals(normalizedEventType)
                || USER_WITHDRAWN_EVENT_TYPE.equals(normalizedEventType);
    }

    private void executeIdempotent(String eventId, String rawEventType, Long userId, Runnable action) {
        try {
            idempotentConsumerService.executeIdempotent(eventId, IDEMPOTENT_EVENT_TYPE, () -> {
                action.run();
                log.info("[ProfileUserEventConsumer] projection synced. eventId={}, eventType={}, userId={}",
                        eventId, rawEventType, userId);
                return null;
            });
        } catch (Exception e) {
            log.error("[ProfileUserEventConsumer] consume failed. eventId={}, eventType={}, userId={}",
                    eventId, rawEventType, userId, e);
            throw e;
        }
    }

    private String normalizeEventType(String eventType) {
        if (!StringUtils.hasText(eventType)) {
            return "";
        }
        String normalized = eventType.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("_+", "_")
                .toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "USERCREATED" -> USER_CREATED_EVENT_TYPE;
            case "USEREMAILCHANGED" -> USER_EMAIL_CHANGED_EVENT_TYPE;
            case "USERWITHDRAWN" -> USER_WITHDRAWN_EVENT_TYPE;
            default -> normalized;
        };
    }
}
