package com.example.profile.consumer;

import com.example.core.util.JsonDeserializationException;
import com.example.core.util.JsonSerializationException;
import com.example.core.util.JsonUtils;
import com.example.event.inbox.InboxEnqueueService;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventConsumer {

    private final InboxEnqueueService inboxEnqueueService;
    private final ProfileUserEventProcessor profileUserEventProcessor;
    private final ProfileUserEventRoutingProperties profileUserEventRoutingProperties;

    @KafkaListener(topics = "user-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consume(ConsumerRecord<String, Object> record) {
        String message = normalizePayload(record == null ? null : record.value());
        if (message == null) {
            return;
        }

        JsonNode payload = parsePayload(message);
        if (payload == null) {
            return;
        }

        String eventId = readText(payload, "eventId");
        String eventType = readText(payload, "eventType");
        if (!hasRequiredMetadata(eventId, eventType)) {
            return;
        }

        if (!profileUserEventProcessor.supports(eventType)) {
            log.debug("[ProfileUserEventConsumer] ignore unsupported type. eventId={}, eventType={}", eventId, eventType);
            return;
        }

        if (profileUserEventRoutingProperties.isInboxMode()) {
            boolean enqueued = inboxEnqueueService.enqueue(
                    ProfileUserEventInboxHandler.CONSUMER_NAME,
                    eventId,
                    eventType,
                    message
            );
            if (!enqueued) {
                log.debug("[ProfileUserEventConsumer] inbox enqueue skipped. eventId={}, eventType={}", eventId, eventType);
            }
            return;
        }

        profileUserEventProcessor.process(message, eventId, eventType);
    }

    private String normalizePayload(Object rawMessage) {
        if (rawMessage == null) {
            log.warn("[ProfileUserEventConsumer] skip null payload");
            return null;
        }
        if (rawMessage instanceof String text) {
            return text;
        }
        try {
            return JsonUtils.toJson(rawMessage);
        } catch (JsonSerializationException e) {
            log.warn("[ProfileUserEventConsumer] skip unsupported payload type. payloadType={}",
                    rawMessage.getClass().getName());
            return null;
        }
    }

    private JsonNode parsePayload(String message) {
        try {
            return JsonUtils.fromJson(message, JsonNode.class);
        } catch (JsonDeserializationException e) {
            log.warn("[ProfileUserEventConsumer] skip malformed message. payload={}", message);
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
}
