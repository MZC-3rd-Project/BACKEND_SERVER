package com.example.event.consumer;

import com.example.core.util.JsonDeserializationException;
import com.example.core.util.JsonSerializationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.util.StringUtils;

@Slf4j
public abstract class AbstractEnvelopeRoutingConsumer {

    protected final void consumeRecord(ConsumerRecord<String, Object> record) {
        String message = normalizePayload(record == null ? null : record.value());
        if (message == null) {
            return;
        }

        JsonEventEnvelope envelope = parseEnvelope(message);
        if (envelope == null) {
            return;
        }

        if (!hasRequiredMetadata(envelope)) {
            return;
        }

        if (!supports(envelope.getEventType())) {
            log.debug("{} ignore unsupported type. eventId={}, eventType={}",
                    consumerLogName(), envelope.getEventId(), envelope.getEventType());
            return;
        }

        if (routeToInbox(envelope)) {
            boolean enqueued = enqueue(envelope, message);
            if (!enqueued) {
                log.debug("{} inbox enqueue skipped. eventId={}, eventType={}",
                        consumerLogName(), envelope.getEventId(), envelope.getEventType());
            }
            return;
        }

        handleDirect(message, envelope);
    }

    protected String consumerLogName() {
        return "[" + getClass().getSimpleName() + "]";
    }

    protected abstract boolean supports(String eventType);

    protected abstract void handleDirect(String message, JsonEventEnvelope envelope);

    protected boolean routeToInbox(JsonEventEnvelope envelope) {
        return false;
    }

    protected boolean enqueue(JsonEventEnvelope envelope, String message) {
        return false;
    }

    private String normalizePayload(Object rawMessage) {
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

    private JsonEventEnvelope parseEnvelope(String message) {
        try {
            return KafkaMessageSupport.readJsonEnvelope(message);
        } catch (JsonDeserializationException exception) {
            log.warn("{} skip malformed message. payload={}", consumerLogName(), message);
            return null;
        }
    }

    private boolean hasRequiredMetadata(EventEnvelope envelope) {
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
}
