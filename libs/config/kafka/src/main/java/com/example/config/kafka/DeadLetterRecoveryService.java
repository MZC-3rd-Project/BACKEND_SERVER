package com.example.config.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeadLetterRecoveryService {

    private static final Set<DeadLetterMessage.DlqStatus> ACTIVE_STATUSES = Set.of(
            DeadLetterMessage.DlqStatus.UNRESOLVED,
            DeadLetterMessage.DlqStatus.RETRYING
    );

    private final DeadLetterMessageRepository deadLetterMessageRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final DeadLetterProperties deadLetterProperties;
    private final DeadLetterAlertPublisher deadLetterAlertPublisher;

    public void recover() {
        resolveProcessedMessages();
        if (!deadLetterProperties.isRetryEnabled()) {
            return;
        }

        List<DeadLetterMessage> targets = deadLetterMessageRepository.findRetryTargets(
                ACTIVE_STATUSES,
                LocalDateTime.now(),
                PageRequest.of(0, Math.max(1, deadLetterProperties.getRetryBatchSize()))
        );
        for (DeadLetterMessage target : targets) {
            retry(target.getId());
        }
    }

    protected void resolveProcessedMessages() {
        List<DeadLetterMessage> activeMessages = deadLetterMessageRepository.findActiveMessagesWithEventId(
                ACTIVE_STATUSES,
                PageRequest.of(0, Math.max(1, deadLetterProperties.getRetryBatchSize()))
        );
        for (DeadLetterMessage message : activeMessages) {
            processedEventRepository.findByEventId(message.getEventId())
                    .filter(processed -> processed.getStatus() == ProcessedEvent.ProcessingStatus.PROCESSED)
                    .ifPresent(processed -> {
                        message.markAsResolved();
                        deadLetterMessageRepository.save(message);
                    });
        }
    }

    public void retry(Long deadLetterMessageId) {
        DeadLetterMessage message = deadLetterMessageRepository.findById(deadLetterMessageId).orElse(null);
        if (message == null || message.getStatus() == DeadLetterMessage.DlqStatus.RESOLVED) {
            return;
        }
        if (!hasText(message.getEventId()) || !hasText(message.getPayload()) || !hasText(message.getTopic())) {
            return;
        }

        if (isProcessed(message.getEventId())) {
            markResolved(deadLetterMessageId);
            return;
        }
        if (!isRetryDue(message)) {
            return;
        }

        markRetrying(deadLetterMessageId, nextDispatchCheckAt(message.getRetryCount()));
        try {
            kafkaTemplate.send(message.getTopic(), message.getKey(), message.getPayload())
                    .get(Math.max(1, deadLetterProperties.getRepublishTimeoutSeconds()), TimeUnit.SECONDS);
            log.info("Dead letter message re-published. deadLetterId={}, topic={}, eventId={}",
                    deadLetterMessageId, message.getTopic(), message.getEventId());
        } catch (Exception exception) {
            recordRetryDispatchFailure(deadLetterMessageId, shrinkErrorMessage(exception));
        }
    }

    protected void markResolved(Long deadLetterMessageId) {
        deadLetterMessageRepository.findById(deadLetterMessageId)
                .ifPresent(message -> {
                    message.markAsResolved();
                    deadLetterMessageRepository.save(message);
                });
    }

    protected void markRetrying(Long deadLetterMessageId, LocalDateTime nextRetryAt) {
        deadLetterMessageRepository.findById(deadLetterMessageId)
                .ifPresent(message -> {
                    message.markAsRetrying(LocalDateTime.now(), nextRetryAt);
                    deadLetterMessageRepository.save(message);
                });
    }

    protected void recordRetryDispatchFailure(Long deadLetterMessageId, String errorMessage) {
        deadLetterMessageRepository.findById(deadLetterMessageId).ifPresent(message -> {
            message.recordRetryFailure(
                    message.getTopic(),
                    message.getPartition(),
                    message.getOffset(),
                    message.getKey(),
                    message.getPayload(),
                    errorMessage,
                    message.getEventType(),
                    message.getConsumerAttemptCount(),
                    calculateNextRetryAt(message.getRetryCount() + 1)
            );
            int threshold = Math.max(1, deadLetterProperties.getRetryFailureAlertThreshold());
            if (message.shouldAlertRetryFailure(threshold)
                    && deadLetterAlertPublisher.publish(message, DlqAlertType.DLQ_RETRY_THRESHOLD_EXCEEDED)) {
                message.markRetryAlerted();
            }
            log.warn("Dead letter re-publish failed. deadLetterId={}, eventId={}, retryCount={}, error={}",
                    deadLetterMessageId, message.getEventId(), message.getRetryCount(), errorMessage);
            deadLetterMessageRepository.save(message);
        });
    }

    private boolean isProcessed(String eventId) {
        return processedEventRepository.findByEventId(eventId)
                .map(processed -> processed.getStatus() == ProcessedEvent.ProcessingStatus.PROCESSED)
                .orElse(false);
    }

    private boolean isRetryDue(DeadLetterMessage message) {
        LocalDateTime nextRetryAt = message.getNextRetryAt();
        return nextRetryAt == null || !nextRetryAt.isAfter(LocalDateTime.now());
    }

    private LocalDateTime nextDispatchCheckAt(int retryCount) {
        return calculateNextRetryAt(Math.max(1, retryCount + 1));
    }

    private LocalDateTime calculateNextRetryAt(int retryCount) {
        long delay = Math.max(1, deadLetterProperties.getRetryInitialDelaySeconds());
        int multiplier = Math.max(1, deadLetterProperties.getRetryBackoffMultiplier());
        long maxDelay = Math.max(delay, deadLetterProperties.getRetryMaxDelaySeconds());
        for (int i = 1; i < retryCount; i++) {
            delay = Math.min(delay * multiplier, maxDelay);
        }
        return LocalDateTime.now().plusSeconds(delay);
    }

    private String shrinkErrorMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return "Dead letter re-publish failed";
        }
        String message = throwable.getMessage();
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
