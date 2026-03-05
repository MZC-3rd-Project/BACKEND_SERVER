package com.example.event.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Semaphore;

@Slf4j
public class OutboxRelayScheduler {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final OutboxProperties outboxProperties;
    private final Semaphore inFlightLimiter;

    public OutboxRelayScheduler(
            OutboxRepository outboxRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            TransactionTemplate transactionTemplate,
            OutboxProperties outboxProperties
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = transactionTemplate;
        this.outboxProperties = outboxProperties;
        int maxInFlight = Math.max(1, outboxProperties.getRelay().getMaxInFlight());
        this.inFlightLimiter = new Semaphore(maxInFlight);
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay.fixed-delay-ms:5000}")
    public void relayPendingMessages() {
        recoverStaleSendingMessages();

        long fetchBeforeSeconds = Math.max(1, outboxProperties.getRelay().getFetchBeforeSeconds());
        LocalDateTime fetchBefore = LocalDateTime.now().minusSeconds(fetchBeforeSeconds);
        List<OutboxMessage> pendingMessages = fetchPendingMessages(fetchBefore);
        if (pendingMessages == null || pendingMessages.isEmpty()) {
            return;
        }

        for (OutboxMessage message : pendingMessages) {
            if (!inFlightLimiter.tryAcquire()) {
                log.debug("Relay in-flight limit reached: limit={}", outboxProperties.getRelay().getMaxInFlight());
                break;
            }
            relayMessageAsync(message);
        }
    }

    public List<OutboxMessage> fetchPendingMessages(LocalDateTime before) {
        int batchSize = Math.max(1, outboxProperties.getRelay().getBatchSize());
        long baseRetryDelaySeconds = Math.max(1, outboxProperties.getRelay().getBaseRetryDelaySeconds());
        long maxRetryDelaySeconds = Math.max(baseRetryDelaySeconds, outboxProperties.getRelay().getMaxRetryDelaySeconds());
        LocalDateTime now = LocalDateTime.now();
        return transactionTemplate.execute(status -> outboxRepository.findPendingMessagesForRelay(
                OutboxStatus.PENDING.name(), before, now, baseRetryDelaySeconds, maxRetryDelaySeconds, batchSize));
    }

    private void recoverStaleSendingMessages() {
        long staleThresholdSeconds = Math.max(1, outboxProperties.getRelay().getSendingStaleThresholdSeconds());
        LocalDateTime staleBefore = LocalDateTime.now().minusSeconds(staleThresholdSeconds);
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
            recoverStaleSendingMessage(staleId, staleBefore);
        }
    }

    private void recoverStaleSendingMessage(Long messageId, LocalDateTime staleBefore) {
        boolean claimed = Boolean.TRUE.equals(transactionTemplate.execute(status ->
                outboxRepository.claimStaleSendingMessage(
                        messageId,
                        OutboxStatus.SENDING,
                        staleBefore,
                        LocalDateTime.now()) > 0
        ));
        if (!claimed) {
            return;
        }

        transactionTemplate.executeWithoutResult(status ->
                outboxRepository.findById(messageId).ifPresent(message -> {
                    if (message.getStatus() != OutboxStatus.SENDING) {
                        return;
                    }

                    message.incrementRetryCount();
                    if (message.exceedsMaxRetries(outboxProperties.getRelay().getMaxRetries())) {
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

            if (current.exceedsMaxRetries(outboxProperties.getRelay().getMaxRetries())) {
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
        Integer updated = transactionTemplate.execute(status ->
                outboxRepository.markAsPublishedById(
                        messageId,
                        OutboxStatus.SENDING,
                        OutboxStatus.PUBLISHED,
                        LocalDateTime.now())
        );
        if (updated != null && updated > 0) {
            log.info("Relay publish success: eventId={}", eventId);
            return;
        }
        log.debug("Skip publish ack for non-SENDING message: eventId={}", eventId);
    }

    private void handlePublishFailure(Long messageId, String eventId, Throwable throwable) {
        String errorMessage = shrinkErrorMessage(throwable);

        transactionTemplate.executeWithoutResult(status ->
                outboxRepository.findById(messageId).ifPresent(message -> {
                    if (message.getStatus() != OutboxStatus.SENDING) {
                        return;
                    }

                    message.incrementRetryCount();
                    if (message.exceedsMaxRetries(outboxProperties.getRelay().getMaxRetries())) {
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
        int maxLength = Math.max(32, outboxProperties.getRelay().getMaxErrorMessageLength());
        if (message.length() <= maxLength) {
            return message;
        }
        return message.substring(0, maxLength);
    }
}
