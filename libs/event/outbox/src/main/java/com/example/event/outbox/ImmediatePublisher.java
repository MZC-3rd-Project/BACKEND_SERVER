package com.example.event.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
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
        Long messageId = event.getOutboxMessage().getId();
        try {
            if (!tryMarkAsSending(messageId)) {
                log.debug("Message already picked up by another publisher: id={}", messageId);
                return;
            }

            OutboxMessage message = outboxRepository.findById(messageId).orElse(null);
            if (message == null) {
                return;
            }

            kafkaTemplate.send(message.getTopic(), message.getAggregateId(), message.getPayload())
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            markAsPublished(messageId);
                            log.debug("Immediate publish success: eventId={}", message.getEventId());
                        } else {
                            revertToPending(messageId);
                            log.warn("Immediate publish failed, relay will retry: eventId={}, error={}",
                                    message.getEventId(), ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            revertToPending(messageId);
            log.warn("Immediate publish error, relay will retry: id={}", messageId, e);
        }
    }

    @Transactional
    public boolean tryMarkAsSending(Long messageId) {
        return outboxRepository.updateStatusById(messageId, OutboxStatus.PENDING, OutboxStatus.SENDING) > 0;
    }

    @Transactional
    public void markAsPublished(Long messageId) {
        outboxRepository.findById(messageId).ifPresent(msg -> {
            msg.markAsPublished();
            outboxRepository.save(msg);
        });
    }

    @Transactional
    public void revertToPending(Long messageId) {
        outboxRepository.findById(messageId).ifPresent(msg -> {
            msg.revertToPending();
            outboxRepository.save(msg);
        });
    }
}
