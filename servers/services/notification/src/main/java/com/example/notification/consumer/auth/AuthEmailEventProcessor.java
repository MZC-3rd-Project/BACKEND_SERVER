package com.example.notification.consumer.auth;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.AbstractIdempotentEventSpecProcessor;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import com.example.notification.service.email.AuthEmailEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@InboxConsumerBinding(consumerName = AuthEmailEventProcessor.CONSUMER_NAME)
public class AuthEmailEventProcessor extends AbstractIdempotentEventSpecProcessor {

    public static final String CONSUMER_NAME = "notification-auth-events-consumer";
    private static final String AUTH_EVENT = "AUTH_EVENT";
    private static final String EMAIL_CONFIRM_EVENT_TYPE = "EMAIL_CONFIRM_EVENT";

    private final Map<String, EventSpec<AuthEmailConfirmEventMessage>> eventSpecs;

    public AuthEmailEventProcessor(
            IdempotentConsumerService idempotentConsumerService,
            AuthEmailEventService authEmailEventService
    ) {
        super(idempotentConsumerService);
        this.eventSpecs = Map.of(
                EMAIL_CONFIRM_EVENT_TYPE,
                EventSpec.of(
                        AuthEmailConfirmEventMessage.class,
                        this::hasRequiredPayloadFields,
                        event -> authEmailEventService.sendEmailConfirm(
                                event.getEventId(),
                                event.getEmail(),
                                event.getVerificationCode()
                        )
                )
        );
    }

    @Override
    protected String idempotentEventType() {
        return AUTH_EVENT;
    }

    @Override
    protected Map<String, EventSpec<AuthEmailConfirmEventMessage>> eventSpecs() {
        return eventSpecs;
    }

    @Override
    protected void onInvalidEnvelope(String eventId, String eventType, String message) {
        log.warn("Skip invalid auth event. eventId={}, eventType={}", eventId, eventType);
    }

    @Override
    protected <T extends EventEnvelope> void onInvalidPayload(T event, String message, String eventId, String eventType) {
        log.warn("Skip EMAIL_CONFIRM_EVENT due to missing payload. eventId={}", eventId);
    }

    private boolean hasRequiredPayloadFields(AuthEmailConfirmEventMessage event) {
        return hasText(event.getEmail()) && hasText(event.getVerificationCode());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
