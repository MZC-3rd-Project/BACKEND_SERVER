package com.example.profile.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.util.JsonDeserializationException;
import com.example.core.util.JsonUtils;
import com.example.profile.service.command.ProfileProjectionSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileUserEventProcessor {

    private static final String IDEMPOTENT_EVENT_TYPE = "USER_EVENT";

    public static final String USER_CREATED_EVENT_TYPE = "UserCreated";
    public static final String USER_EMAIL_CHANGED_EVENT_TYPE = "UserEmailChanged";
    public static final String USER_WITHDRAWN_EVENT_TYPE = "UserWithdrawn";

    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
            USER_CREATED_EVENT_TYPE,
            USER_EMAIL_CHANGED_EVENT_TYPE,
            USER_WITHDRAWN_EVENT_TYPE
    );

    private final IdempotentConsumerService idempotentConsumerService;
    private final ProfileProjectionSyncService profileProjectionSyncService;

    public boolean supports(String eventType) {
        return StringUtils.hasText(eventType) && SUPPORTED_EVENT_TYPES.contains(eventType);
    }

    public void process(String message, String eventId, String eventType) {
        if (!supports(eventType)) {
            log.debug("[ProfileUserEventProcessor] ignore unsupported type. eventId={}, eventType={}", eventId, eventType);
            return;
        }

        switch (eventType) {
            case USER_CREATED_EVENT_TYPE -> processUserCreated(message, eventId, eventType);
            case USER_EMAIL_CHANGED_EVENT_TYPE -> processUserEmailChanged(message, eventId, eventType);
            case USER_WITHDRAWN_EVENT_TYPE -> processUserWithdrawn(message, eventId, eventType);
            default -> log.debug("[ProfileUserEventProcessor] ignore unsupported type. eventId={}, eventType={}", eventId, eventType);
        }
    }

    private void processUserCreated(String message, String eventId, String rawEventType) {
        UserCreatedEventDto event = parseEvent(message, UserCreatedEventDto.class, rawEventType);
        if (event == null || event.userId() == null
                || !StringUtils.hasText(event.email())
                || !StringUtils.hasText(event.nickname())) {
            log.warn("[ProfileUserEventProcessor] skip invalid payload. eventId={}, eventType={}, userId={}",
                    eventId, rawEventType, event == null ? null : event.userId());
            return;
        }

        executeIdempotent(eventId, rawEventType, event.userId(),
                () -> profileProjectionSyncService.upsertFromUserCreated(event.userId(), event.email(), event.nickname()));
    }

    private void processUserEmailChanged(String message, String eventId, String rawEventType) {
        UserEmailChangedEventDto event = parseEvent(message, UserEmailChangedEventDto.class, rawEventType);
        if (event == null || event.userId() == null || !StringUtils.hasText(event.newEmail())) {
            log.warn("[ProfileUserEventProcessor] skip invalid payload. eventId={}, eventType={}, userId={}",
                    eventId, rawEventType, event == null ? null : event.userId());
            return;
        }

        executeIdempotent(eventId, rawEventType, event.userId(),
                () -> profileProjectionSyncService.applyUserEmailChanged(event.userId(), event.newEmail()));
    }

    private void processUserWithdrawn(String message, String eventId, String rawEventType) {
        UserWithdrawnEventDto event = parseEvent(message, UserWithdrawnEventDto.class, rawEventType);
        if (event == null || event.userId() == null) {
            log.warn("[ProfileUserEventProcessor] skip invalid payload. eventId={}, eventType={}, userId={}",
                    eventId, rawEventType, event == null ? null : event.userId());
            return;
        }

        executeIdempotent(eventId, rawEventType, event.userId(),
                () -> profileProjectionSyncService.withdrawProjection(event.userId()));
    }

    private <T> T parseEvent(String message, Class<T> clazz, String eventType) {
        try {
            return JsonUtils.fromJson(message, clazz);
        } catch (JsonDeserializationException e) {
            log.warn("[ProfileUserEventProcessor] payload parse failed. eventType={}, payload={}", eventType, message);
            return null;
        }
    }

    private void executeIdempotent(String eventId, String rawEventType, Long userId, Runnable action) {
        try {
            idempotentConsumerService.executeIdempotent(eventId, IDEMPOTENT_EVENT_TYPE, () -> {
                action.run();
                log.info("[ProfileUserEventProcessor] projection synced. eventId={}, eventType={}, userId={}",
                        eventId, rawEventType, userId);
                return null;
            });
        } catch (Exception e) {
            log.error("[ProfileUserEventProcessor] consume failed. eventId={}, eventType={}, userId={}",
                    eventId, rawEventType, userId, e);
            throw e;
        }
    }
}
