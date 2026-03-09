package com.example.event.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.util.JsonDeserializationException;
import com.example.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Map;

@Slf4j
public abstract class AbstractIdempotentEventSpecProcessor implements EventMessageProcessor {

    private final IdempotentConsumerService idempotentConsumerService;

    protected AbstractIdempotentEventSpecProcessor(IdempotentConsumerService idempotentConsumerService) {
        this.idempotentConsumerService = idempotentConsumerService;
    }

    protected abstract Map<String, ? extends EventSpec<? extends EventEnvelope>> eventSpecs();

    @Override
    public final boolean supports(String eventType) {
        return StringUtils.hasText(eventType) && eventSpecs().containsKey(eventType);
    }

    @Override
    public final void process(String message, String eventId, String eventType) {
        if (!StringUtils.hasText(eventId) || !StringUtils.hasText(eventType)) {
            onInvalidEnvelope(eventId, eventType, message);
            return;
        }

        EventSpec<? extends EventEnvelope> eventSpec = eventSpecs().get(eventType);
        if (eventSpec == null) {
            onUnsupportedEventType(eventId, eventType);
            return;
        }
        processEvent(cast(eventSpec), message, eventId, eventType);
    }

    protected abstract String idempotentEventType();

    protected String processorLogName() {
        return "[" + getClass().getSimpleName() + "]";
    }

    protected void onInvalidEnvelope(String eventId, String eventType, String message) {
        log.warn("{} skip invalid envelope. eventId={}, eventType={}", processorLogName(), eventId, eventType);
    }

    protected void onUnsupportedEventType(String eventId, String eventType) {
        log.debug("{} ignore unsupported type. eventId={}, eventType={}", processorLogName(), eventId, eventType);
    }

    protected <T extends EventEnvelope> void onInvalidPayload(T event, String message, String eventId, String eventType) {
        log.warn("{} skip invalid payload. eventId={}, eventType={}", processorLogName(), eventId, eventType);
    }

    protected void onParseFailure(String message, String eventType, JsonDeserializationException exception) {
        log.warn("{} payload parse failed. eventType={}, payload={}", processorLogName(), eventType, message);
    }

    protected <T extends EventEnvelope> void onProcessed(T event, String eventId, String eventType) {
        log.info("{} processed event. eventId={}, eventType={}", processorLogName(), eventId, eventType);
    }

    protected <T extends EventEnvelope> void onProcessingException(
            T event,
            String message,
            String eventId,
            String eventType,
            Exception exception
    ) {
        log.error("{} consume failed. eventId={}, eventType={}", processorLogName(), eventId, eventType, exception);
        throw propagate(exception);
    }

    private <T extends EventEnvelope> void processEvent(
            EventSpec<T> eventSpec,
            String message,
            String eventId,
            String eventType
    ) {
        T event = parseEvent(message, eventSpec.eventClass(), eventType);
        if (event == null || !eventSpec.validator().test(event)) {
            onInvalidPayload(event, message, eventId, eventType);
            return;
        }

        executeIdempotent(event, message, eventId, eventType, () -> eventSpec.action().accept(event));
    }

    private <T> T parseEvent(String message, Class<T> eventClass, String eventType) {
        try {
            return JsonUtils.fromJson(KafkaMessageSupport.normalizePayload(message), eventClass);
        } catch (JsonDeserializationException exception) {
            onParseFailure(message, eventType, exception);
            return null;
        }
    }

    private <T extends EventEnvelope> void executeIdempotent(
            T event,
            String message,
            String eventId,
            String eventType,
            Runnable action
    ) {
        try {
            idempotentConsumerService.executeIdempotent(eventId, idempotentEventType(), () -> {
                action.run();
                onProcessed(event, eventId, eventType);
                return null;
            });
        } catch (Exception exception) {
            onProcessingException(event, message, eventId, eventType, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends EventEnvelope> EventSpec<T> cast(EventSpec<? extends EventEnvelope> eventSpec) {
        return (EventSpec<T>) eventSpec;
    }

    protected final RuntimeException propagate(Exception exception) {
        if (exception instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(exception);
    }
}
