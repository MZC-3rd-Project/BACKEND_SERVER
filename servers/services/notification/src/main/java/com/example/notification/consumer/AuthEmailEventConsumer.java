package com.example.notification.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.util.JsonUtils;
import com.example.notification.service.email.AuthEmailEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthEmailEventConsumer {

    private static final String AUTH_EVENT = "AUTH_EVENT";
    private static final String EMAIL_CONFIRM_EVENT_TYPE = "EMAIL_CONFIRM_EVENT";

    private final IdempotentConsumerService idempotentConsumerService;
    private final AuthEmailEventService authEmailEventService;

    @KafkaListener(topics = "auth-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consumeAuth(String message) {
        AuthEmailConfirmEventMessage event = JsonUtils.fromJson(message, AuthEmailConfirmEventMessage.class);
        if (!hasRequiredEnvelope(event)) {
            return;
        }

        idempotentConsumerService.executeIdempotent(event.getEventId(), AUTH_EVENT, () -> {
            if (!EMAIL_CONFIRM_EVENT_TYPE.equals(normalizeEventType(event.getEventType()))) {
                log.debug("Ignore auth event type: {}", event.getEventType());
                return null;
            }
            if (!hasRequiredPayload(event)) {
                log.warn("Skip EMAIL_CONFIRM_EVENT due to missing payload. eventId={}", event.getEventId());
                return null;
            }

            authEmailEventService.sendEmailConfirm(event.getEventId(), event.getEmail(), event.getVerificationCode());
            return null;
        });
    }

    private boolean hasRequiredEnvelope(AuthEmailConfirmEventMessage event) {
        if (event == null) {
            log.warn("Skip invalid auth event. payload is null");
            return false;
        }
        if (event.getEventId() == null || event.getEventType() == null) {
            log.warn("Skip invalid auth event. eventId={}, eventType={}", event.getEventId(), event.getEventType());
            return false;
        }
        return true;
    }

    private boolean hasRequiredPayload(AuthEmailConfirmEventMessage event) {
        return hasText(event.getEmail()) && hasText(event.getVerificationCode());
    }

    private String normalizeEventType(String eventType) {
        return eventType == null ? "" : eventType.toUpperCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
