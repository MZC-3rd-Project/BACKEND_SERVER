package com.example.event.outbox;

import com.example.core.util.JsonUtils;
import com.example.event.DomainEvent;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService implements EventPublisher {

    private final OutboxRepository outboxRepository;

    @Override
    @Transactional
    public void publish(DomainEvent event) {
        publish(event, EventMetadata.of("UNKNOWN", "UNKNOWN"));
    }

    @Override
    @Transactional
    public void publish(DomainEvent event, EventMetadata metadata) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", event.getEventId());
        envelope.put("eventType", event.getEventTypeName());
        envelope.putAll(event.getPayload());
        String payload = JsonUtils.toJson(envelope);

        OutboxMessage message = OutboxMessage.create(
                event.getEventId(),
                metadata.aggregateType(),
                metadata.aggregateId(),
                event.getTopic(),
                event.getEventTypeName(),
                payload,
                metadata.correlationId(),
                metadata.causationId()
        );

        outboxRepository.save(message);
        log.debug("Outbox message saved: eventId={}, type={}", event.getEventId(), event.getEventTypeName());
    }
}
