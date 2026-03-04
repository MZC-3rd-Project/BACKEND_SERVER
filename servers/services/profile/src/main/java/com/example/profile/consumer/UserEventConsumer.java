package com.example.profile.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.util.JsonDeserializationException;
import com.example.core.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.example.profile.service.command.ProfileProjectionSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventConsumer {

    private static final String IDEMPOTENT_EVENT_TYPE = "USER_EVENT";
    private static final String USER_CREATED_EVENT_TYPE = "USERCREATED";
    private static final String USER_EMAIL_CHANGED_EVENT_TYPE = "USEREMAILCHANGED";
    private static final String USER_WITHDRAWN_EVENT_TYPE = "USERWITHDRAWN";

    private final IdempotentConsumerService idempotentConsumerService;
    private final ProfileProjectionSyncService profileProjectionSyncService;

    @KafkaListener(topics = "user-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consume(String message) {
        Map<String, Object> messageMap = parseMessageMap(message);
        if (messageMap == null) {
            return;
        }

        UserEventEnvelope envelope = parseEnvelope(messageMap);
        if (!hasRequiredEnvelope(envelope)) {
            return;
        }

        String normalizedEventType = normalizeEventType(envelope.eventType());
        if (!isSupportedType(normalizedEventType)) {
            log.debug("[ProfileUserEventConsumer] ignore unsupported type. eventId={}, eventType={}",
                    envelope.eventId(), envelope.eventType());
            return;
        }

        UserProjectionPayload payload = parsePayload(messageMap, normalizedEventType);
        if (!hasRequiredPayload(payload, normalizedEventType)) {
            log.warn("[ProfileUserEventConsumer] skip invalid payload. eventId={}, eventType={}, userId={}",
                    envelope.eventId(), envelope.eventType(), payload == null ? null : payload.userId());
            return;
        }

        try {
            idempotentConsumerService.executeIdempotent(envelope.eventId(), IDEMPOTENT_EVENT_TYPE, () -> {
                routeProjectionEvent(payload, normalizedEventType);
                log.info("[ProfileUserEventConsumer] projection synced. eventId={}, eventType={}, userId={}",
                        envelope.eventId(), envelope.eventType(), payload.userId());
                return null;
            });
        } catch (Exception e) {
            log.error("[ProfileUserEventConsumer] consume failed. eventId={}, eventType={}, userId={}",
                    envelope.eventId(), envelope.eventType(), payload.userId(), e);
            throw e;
        }
    }

    private Map<String, Object> parseMessageMap(String message) {
        try {
            return JsonUtils.fromJson(message, new TypeReference<>() {
            });
        } catch (JsonDeserializationException e) {
            log.warn("[ProfileUserEventConsumer] skip malformed message. payload={}", message);
            return null;
        }
    }

    private UserEventEnvelope parseEnvelope(Map<String, Object> messageMap) {
        try {
            return JsonUtils.fromMap(messageMap, UserEventEnvelope.class);
        } catch (IllegalArgumentException e) {
            log.warn("[ProfileUserEventConsumer] skip message. envelope parse failed. payload={}", messageMap);
            return null;
        }
    }

    private boolean hasRequiredEnvelope(UserEventEnvelope envelope) {
        if (envelope == null) {
            return false;
        }
        if (!StringUtils.hasText(envelope.eventId()) || !StringUtils.hasText(envelope.eventType())) {
            log.warn("[ProfileUserEventConsumer] skip invalid envelope. eventId={}, eventType={}",
                    envelope.eventId(), envelope.eventType());
            return false;
        }
        return true;
    }

    private UserProjectionPayload parsePayload(Map<String, Object> messageMap, String normalizedEventType) {
        try {
            return switch (normalizedEventType) {
                case USER_CREATED_EVENT_TYPE -> JsonUtils.fromMap(messageMap, UserCreatedPayload.class);
                case USER_EMAIL_CHANGED_EVENT_TYPE -> JsonUtils.fromMap(messageMap, UserEmailChangedPayload.class);
                case USER_WITHDRAWN_EVENT_TYPE -> JsonUtils.fromMap(messageMap, UserWithdrawnPayload.class);
                default -> null;
            };
        } catch (IllegalArgumentException e) {
            log.warn("[ProfileUserEventConsumer] payload parse failed. eventType={}, payload={}",
                    normalizedEventType, messageMap);
            return null;
        }
    }

    private boolean hasRequiredPayload(UserProjectionPayload payload, String normalizedEventType) {
        if (payload == null || payload.userId() == null) {
            return false;
        }

        return switch (normalizedEventType) {
            case USER_CREATED_EVENT_TYPE -> {
                UserCreatedPayload userCreated = (UserCreatedPayload) payload;
                yield StringUtils.hasText(userCreated.email()) && StringUtils.hasText(userCreated.nickname());
            }
            case USER_EMAIL_CHANGED_EVENT_TYPE -> {
                UserEmailChangedPayload emailChanged = (UserEmailChangedPayload) payload;
                yield StringUtils.hasText(emailChanged.newEmail());
            }
            case USER_WITHDRAWN_EVENT_TYPE -> true;
            default -> false;
        };
    }

    private boolean isSupportedType(String normalizedEventType) {
        return USER_CREATED_EVENT_TYPE.equals(normalizedEventType)
                || USER_EMAIL_CHANGED_EVENT_TYPE.equals(normalizedEventType)
                || USER_WITHDRAWN_EVENT_TYPE.equals(normalizedEventType);
    }

    private void routeProjectionEvent(UserProjectionPayload payload, String normalizedEventType) {
        switch (normalizedEventType) {
            case USER_CREATED_EVENT_TYPE -> {
                UserCreatedPayload userCreated = (UserCreatedPayload) payload;
                profileProjectionSyncService.upsertFromUserCreated(
                        userCreated.userId(), userCreated.email(), userCreated.nickname()
                );
            }
            case USER_EMAIL_CHANGED_EVENT_TYPE -> {
                UserEmailChangedPayload emailChanged = (UserEmailChangedPayload) payload;
                profileProjectionSyncService.applyUserEmailChanged(
                        emailChanged.userId(), emailChanged.newEmail()
                );
            }
            case USER_WITHDRAWN_EVENT_TYPE ->
                    profileProjectionSyncService.withdrawProjection(payload.userId());
            default -> throw new IllegalArgumentException("Unsupported event type: " + normalizedEventType);
        }
    }

    private String normalizeEventType(String eventType) {
        return eventType == null ? "" : eventType.trim().toUpperCase(Locale.ROOT);
    }
}
