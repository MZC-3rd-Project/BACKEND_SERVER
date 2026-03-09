package com.example.event.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.util.JsonUtils;

public abstract class AbstractIdempotentJsonMessageConsumer<T extends EventEnvelope> {

    private final IdempotentConsumerService idempotentConsumerService;

    protected AbstractIdempotentJsonMessageConsumer(IdempotentConsumerService idempotentConsumerService) {
        this.idempotentConsumerService = idempotentConsumerService;
    }

    protected final void consumeMessage(String message) {
        T event = null;
        try {
            event = JsonUtils.fromJson(message, payloadType());
            if (!hasRequiredEnvelope(event)) {
                onMissingEnvelope(message, event);
                return;
            }
            if (!isValidEvent(event, message)) {
                return;
            }

            T currentEvent = event;
            idempotentConsumerService.executeIdempotent(currentEvent.getEventId(), idempotentEventType(), () -> {
                handleEvent(currentEvent);
                return null;
            });
        } catch (Exception exception) {
            onProcessingException(message, event, exception);
        }
    }

    protected abstract Class<T> payloadType();

    protected abstract String idempotentEventType();

    protected abstract void handleEvent(T event);

    protected boolean isValidEvent(T event, String message) {
        return true;
    }

    protected void onMissingEnvelope(String message, T event) {
    }

    protected void onProcessingException(String message, T event, Exception exception) {
        throw propagate(exception);
    }

    protected final RuntimeException propagate(Exception exception) {
        if (exception instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(exception);
    }

    private boolean hasRequiredEnvelope(T event) {
        return event != null
                && hasText(event.getEventId())
                && hasText(event.getEventType());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
