package com.example.event.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImmediatePublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OutboxRepository outboxRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOutboxSaved(OutboxSavedEvent event) {
        OutboxMessage message = event.getOutboxMessage();
        try {
            kafkaTemplate.send(message.getTopic(), message.getAggregateId(), message.getPayload())
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            message.markAsPublished();
                            outboxRepository.save(message);
                            log.debug("Immediate publish success: eventId={}", message.getEventId());
                        } else {
                            log.warn("Immediate publish failed, relay will retry: eventId={}, error={}",
                                    message.getEventId(), ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.warn("Immediate publish error, relay will retry: eventId={}", message.getEventId(), e);
        }
    }
}
