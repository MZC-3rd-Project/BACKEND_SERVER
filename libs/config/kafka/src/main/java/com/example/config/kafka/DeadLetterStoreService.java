package com.example.config.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DeadLetterStoreService {

    private static final Set<DeadLetterMessage.DlqStatus> ACTIVE_STATUSES = Set.of(
            DeadLetterMessage.DlqStatus.UNRESOLVED,
            DeadLetterMessage.DlqStatus.RETRYING
    );

    private final DeadLetterMessageRepository deadLetterMessageRepository;
    private final DeadLetterAlertPublisher deadLetterAlertPublisher;
    private final DeadLetterProperties deadLetterProperties;

    @Transactional
    public DeadLetterMessage store(
            String topic,
            Integer partition,
            Long offset,
            String key,
            String payload,
            String errorMessage,
            String eventId,
            String eventType,
            int consumerAttemptCount
    ) {
        if (hasText(eventId)) {
            DeadLetterMessage existing = deadLetterMessageRepository
                    .findFirstByEventIdAndStatusInOrderByCreatedAtDesc(eventId, ACTIVE_STATUSES)
                    .orElse(null);
            if (existing != null) {
                existing.recordRetryFailure(
                        topic,
                        partition,
                        offset,
                        key,
                        payload,
                        errorMessage,
                        eventType,
                        consumerAttemptCount,
                        calculateNextRetryAt(existing.getRetryCount() + 1)
                );
                publishRetryThresholdAlert(existing);
                return deadLetterMessageRepository.save(existing);
            }
        }

        DeadLetterMessage deadLetterMessage = DeadLetterMessage.create(
                topic,
                partition,
                offset,
                key,
                payload,
                errorMessage,
                eventId,
                eventType,
                consumerAttemptCount,
                initialNextRetryAt()
        );
        deadLetterMessage = deadLetterMessageRepository.save(deadLetterMessage);
        publishConsumerThresholdAlert(deadLetterMessage);
        return deadLetterMessageRepository.save(deadLetterMessage);
    }

    private void publishConsumerThresholdAlert(DeadLetterMessage deadLetterMessage) {
        int threshold = Math.max(1, deadLetterProperties.getConsumerFailureAlertThreshold());
        if (!deadLetterMessage.shouldAlertConsumerFailure(threshold)) {
            return;
        }
        if (deadLetterAlertPublisher.publish(deadLetterMessage, DlqAlertType.CONSUMER_FAILURE_THRESHOLD_EXCEEDED)) {
            deadLetterMessage.markConsumerFailureAlerted();
        }
    }

    private void publishRetryThresholdAlert(DeadLetterMessage deadLetterMessage) {
        int threshold = Math.max(1, deadLetterProperties.getRetryFailureAlertThreshold());
        if (!deadLetterMessage.shouldAlertRetryFailure(threshold)) {
            return;
        }
        if (deadLetterAlertPublisher.publish(deadLetterMessage, DlqAlertType.DLQ_RETRY_THRESHOLD_EXCEEDED)) {
            deadLetterMessage.markRetryAlerted();
        }
    }

    private LocalDateTime initialNextRetryAt() {
        if (!deadLetterProperties.isRetryEnabled()) {
            return null;
        }
        return LocalDateTime.now().plusSeconds(Math.max(1, deadLetterProperties.getRetryInitialDelaySeconds()));
    }

    private LocalDateTime calculateNextRetryAt(int retryCount) {
        if (!deadLetterProperties.isRetryEnabled()) {
            return null;
        }
        long delay = Math.max(1, deadLetterProperties.getRetryInitialDelaySeconds());
        int multiplier = Math.max(1, deadLetterProperties.getRetryBackoffMultiplier());
        for (int i = 1; i < retryCount; i++) {
            delay = Math.min(delay * multiplier, Math.max(delay, deadLetterProperties.getRetryMaxDelaySeconds()));
        }
        return LocalDateTime.now().plusSeconds(delay);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
