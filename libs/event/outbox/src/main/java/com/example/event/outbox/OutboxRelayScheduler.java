package com.example.event.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private static final int MAX_RETRIES = 5;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void relayPendingMessages() {
        LocalDateTime fiveSecondsAgo = LocalDateTime.now().minusSeconds(5);
        List<OutboxMessage> pendingMessages =
                outboxRepository.findByStatusAndCreatedBefore(OutboxStatus.PENDING, fiveSecondsAgo);

        for (OutboxMessage message : pendingMessages) {
            if (message.exceedsMaxRetries(MAX_RETRIES)) {
                message.markAsFailed("Max retries exceeded");
                outboxRepository.save(message);
                log.error("Outbox message exceeded max retries: eventId={}", message.getEventId());
                continue;
            }

            try {
                kafkaTemplate.send(message.getTopic(), message.getAggregateId(), message.getPayload())
                        .get(); // blocking for relay
                message.markAsPublished();
                log.info("Relay publish success: eventId={}", message.getEventId());
            } catch (Exception e) {
                message.incrementRetryCount();
                log.warn("Relay publish failed: eventId={}, retry={}, error={}",
                        message.getEventId(), message.getRetryCount(), e.getMessage());
            }
            outboxRepository.save(message);
        }
    }
}
