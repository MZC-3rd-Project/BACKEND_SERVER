package com.example.event.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Semaphore;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private static final int MAX_RETRIES = 5;
    private static final int BATCH_SIZE = 100;
    private static final int MAX_IN_FLIGHT = 32;
    private static final long SENDING_STALE_THRESHOLD_SECONDS = 120;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 240;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Semaphore inFlightLimiter = new Semaphore(MAX_IN_FLIGHT);

    @Scheduled(fixedDelay = 5000)
    public void relayPendingMessages() {
        recoverStaleSendingMessages();

        LocalDateTime fiveSecondsAgo = LocalDateTime.now().minusSeconds(5);
        List<OutboxMessage> pendingMessages = fetchPendingMessages(fiveSecondsAgo);
        if (pendingMessages == null || pendingMessages.isEmpty()) {
            return;
        }

        for (OutboxMessage message : pendingMessages) {
            if (!inFlightLimiter.tryAcquire()) {
                log.debug("Relay in-flight limit reached: limit={}", MAX_IN_FLIGHT);
                break;
            }
            relayMessageAsync(message);
        }
    }

    public List<OutboxMessage> fetchPendingMessages(LocalDateTime before) {
        return transactionTemplate.execute(status -> outboxRepository.findPendingMessagesForRelay(
                OutboxStatus.PENDING.name(), before, BATCH_SIZE));
    }

    private void recoverStaleSendingMessages() {
        LocalDateTime staleBefore = LocalDateTime.now().minusSeconds(SENDING_STALE_THRESHOLD_SECONDS);
        List<Long> staleIds = transactionTemplate.execute(status ->
                outboxRepository.findTop100ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
                                OutboxStatus.SENDING, staleBefore)
                        .stream()
                        .map(OutboxMessage::getId)
                        .toList()
        );

        if (staleIds == null || staleIds.isEmpty()) {
            return;
        }

        for (Long staleId : staleIds) {
            recoverStaleSendingMessage(staleId);
        }
    }

    private void recoverStaleSendingMessage(Long messageId) {
        transactionTemplate.executeWithoutResult(status ->
                outboxRepository.findById(messageId).ifPresent(message -> {
                    if (message.getStatus() != OutboxStatus.SENDING) {
                        return;
                    }

                    message.incrementRetryCount();
                    if (message.exceedsMaxRetries(MAX_RETRIES)) {
                        message.markAsFailed("Recovered stale SENDING and exceeded max retries");
                        log.error("Outbox stale SENDING moved to FAILED: eventId={}, retry={}",
                                message.getEventId(), message.getRetryCount());
                    } else {
                        message.revertToPending();
                        log.warn("Outbox stale SENDING recovered to PENDING: eventId={}, retry={}",
                                message.getEventId(), message.getRetryCount());
                    }
                    outboxRepository.save(message);
                })
        );
    }

    private void relayMessageAsync(OutboxMessage message) {
        try {
            if (!tryMarkAsSending(message.getId(), message.getEventId())) {
                inFlightLimiter.release();
                return;
            }

            kafkaTemplate.send(message.getTopic(), message.getAggregateId(), message.getPayload())
                    .whenComplete((result, ex) -> {
                        try {
                            if (ex == null) {
                                markAsPublished(message.getId(), message.getEventId());
                            } else {
                                handlePublishFailure(message.getId(), message.getEventId(), ex);
                            }
                        } finally {
                            inFlightLimiter.release();
                        }
                    });
        } catch (Exception e) {
            handlePublishFailure(message.getId(), message.getEventId(), e);
            inFlightLimiter.release();
        }
    }

    private boolean tryMarkAsSending(Long messageId, String eventId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            OutboxMessage current = outboxRepository.findById(messageId).orElse(null);
            if (current == null) {
                return false;
            }

            if (current.exceedsMaxRetries(MAX_RETRIES)) {
                current.markAsFailed("Max retries exceeded");
                outboxRepository.save(current);
                log.error("Outbox message exceeded max retries: eventId={}", eventId);
                return false;
            }

            boolean marked = outboxRepository.updateStatusById(messageId, OutboxStatus.PENDING, OutboxStatus.SENDING) > 0;
            if (!marked) {
                log.debug("Outbox message already being processed: eventId={}", eventId);
            }
            return marked;
        }));
    }

    private void markAsPublished(Long messageId, String eventId) {
        transactionTemplate.executeWithoutResult(status ->
                outboxRepository.findById(messageId).ifPresent(message -> {
                    if (message.getStatus() != OutboxStatus.SENDING) {
                        log.debug("Skip publish ack for non-SENDING message: eventId={}, status={}",
                                eventId, message.getStatus());
                        return;
                    }

                    message.markAsPublished();
                    outboxRepository.save(message);
                    log.info("Relay publish success: eventId={}", eventId);
                })
        );
    }

    private void handlePublishFailure(Long messageId, String eventId, Throwable throwable) {
        String errorMessage = shrinkErrorMessage(throwable);

        transactionTemplate.executeWithoutResult(status ->
                outboxRepository.findById(messageId).ifPresent(message -> {
                    if (message.getStatus() != OutboxStatus.SENDING) {
                        return;
                    }

                    message.incrementRetryCount();
                    if (message.exceedsMaxRetries(MAX_RETRIES)) {
                        message.markAsFailed(errorMessage);
                        log.error("Relay publish failed permanently: eventId={}, retry={}, error={}",
                                eventId, message.getRetryCount(), errorMessage);
                    } else {
                        message.revertToPending();
                        log.warn("Relay publish failed: eventId={}, retry={}, error={}",
                                eventId, message.getRetryCount(), errorMessage);
                    }
                    outboxRepository.save(message);
                })
        );
    }

    private String shrinkErrorMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return "Relay publish failed";
        }
        String message = throwable.getMessage();
        if (message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
