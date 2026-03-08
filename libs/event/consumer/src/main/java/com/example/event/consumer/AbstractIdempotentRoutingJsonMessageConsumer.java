package com.example.event.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.util.JsonDeserializationException;
import com.example.core.util.JsonSerializationException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.function.BiFunction;

@Slf4j
public abstract class AbstractIdempotentRoutingJsonMessageConsumer<T extends EventEnvelope>
        extends AbstractIdempotentJsonMessageConsumer<T>
        implements EventMessageProcessor {

    protected AbstractIdempotentRoutingJsonMessageConsumer(IdempotentConsumerService idempotentConsumerService) {
        super(idempotentConsumerService);
    }

    @Override
    protected final boolean isValidEvent(T event, String message) {
        if (!hasRequiredPayload(event, message)) {
            return false;
        }

        RouteSpec<T> routeSpec = resolveRouteSpec(event);
        if (routeSpec == null) {
            return true;
        }

        if (!routeSpec.validator().test(event)) {
            onInvalidPayload(event, message);
            return false;
        }
        return true;
    }

    @Override
    protected final void handleEvent(T event) {
        RouteSpec<T> routeSpec = resolveRouteSpec(event);
        boolean handled = false;
        if (routeSpec == null) {
            onUnsupportedEventType(event);
        } else {
            routeSpec.action().accept(event);
            handled = true;
        }
        afterRoute(event, handled);
    }

    protected boolean hasRequiredPayload(T event, String message) {
        return true;
    }

    protected void onInvalidPayload(T event, String message) {
    }

    protected void onUnsupportedEventType(T event) {
        log.debug("{} ignore unsupported type. eventId={}, eventType={}",
                consumerLogName(), event.getEventId(), event.getEventType());
    }

    protected void afterRoute(T event, boolean handled) {
    }

    protected String normalizeEventType(String eventType) {
        return eventType == null ? "" : eventType.trim().toUpperCase(Locale.ROOT);
    }

    protected String consumerLogName() {
        return "[" + getClass().getSimpleName() + "]";
    }

    @Override
    public final boolean supports(String eventType) {
        return StringUtils.hasText(eventType) && routeSpecs().containsKey(normalizeEventType(eventType));
    }

    @Override
    public final void process(String message, String eventId, String eventType) {
        consumeMessage(message);
    }

    protected abstract java.util.Map<String, RouteSpec<T>> routeSpecs();

    protected final String normalizePayload(ConsumerRecord<String, Object> record) {
        Object rawMessage = record == null ? null : record.value();
        if (rawMessage == null) {
            log.warn("{} skip null payload", consumerLogName());
            return null;
        }
        try {
            return KafkaMessageSupport.normalizePayload(rawMessage);
        } catch (JsonSerializationException exception) {
            log.warn("{} skip unsupported payload type. payloadType={}",
                    consumerLogName(), rawMessage.getClass().getName());
            return null;
        }
    }

    protected final JsonEventEnvelope readEnvelope(String message) {
        try {
            return KafkaMessageSupport.readJsonEnvelope(message);
        } catch (JsonDeserializationException exception) {
            log.warn("{} skip malformed message. payload={}", consumerLogName(), message);
            return null;
        }
    }

    protected final boolean hasRequiredMetadata(EventEnvelope envelope) {
        if (envelope == null
                || !StringUtils.hasText(envelope.getEventId())
                || !StringUtils.hasText(envelope.getEventType())) {
            log.warn("{} skip invalid metadata. eventId={}, eventType={}",
                    consumerLogName(),
                    envelope == null ? null : envelope.getEventId(),
                    envelope == null ? null : envelope.getEventType());
            return false;
        }
        return true;
    }

    protected final void consumeRecord(
            ConsumerRecord<String, Object> record,
            boolean inboxMode,
            BiFunction<JsonEventEnvelope, String, Boolean> enqueueAction
    ) {
        String message = normalizePayload(record);
        if (message == null) {
            return;
        }

        JsonEventEnvelope envelope = readEnvelope(message);
        if (!hasRequiredMetadata(envelope)) {
            return;
        }

        if (!supports(envelope.getEventType())) {
            log.debug("{} ignore unsupported type. eventId={}, eventType={}",
                    consumerLogName(), envelope.getEventId(), envelope.getEventType());
            return;
        }

        if (inboxMode) {
            boolean enqueued = enqueueAction != null && Boolean.TRUE.equals(enqueueAction.apply(envelope, message));
            if (!enqueued) {
                log.debug("{} inbox enqueue skipped. eventId={}, eventType={}",
                        consumerLogName(), envelope.getEventId(), envelope.getEventType());
            }
            return;
        }

        process(message, envelope.getEventId(), envelope.getEventType());
    }

    private RouteSpec<T> resolveRouteSpec(T event) {
        if (event == null) {
            return null;
        }
        return routeSpecs().get(normalizeEventType(event.getEventType()));
    }
}
