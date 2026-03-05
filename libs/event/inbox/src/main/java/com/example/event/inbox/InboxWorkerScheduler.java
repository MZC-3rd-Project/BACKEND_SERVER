package com.example.event.inbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RequiredArgsConstructor
public class InboxWorkerScheduler implements InboxSignalPublisher {

    private final InboxRepository inboxRepository;
    private final InboxHandlerRegistry inboxHandlerRegistry;
    private final InboxProperties inboxProperties;
    private final TaskExecutor inboxTaskExecutor;
    private final TransactionTemplate transactionTemplate;

    private final ConcurrentMap<String, AtomicBoolean> consumerRunGuards = new ConcurrentHashMap<>();

    @Override
    public void signal(String consumerName) {
        if (!inboxProperties.getWorker().isEnabled() || !inboxProperties.isImmediateTriggerEnabled()) {
            return;
        }
        inboxTaskExecutor.execute(() -> processConsumerMessages(consumerName, "signal"));
    }

    @Scheduled(fixedDelayString = "${app.inbox.worker.fixed-delay-ms:2000}")
    public void pollAndProcess() {
        if (!inboxProperties.getWorker().isEnabled()) {
            return;
        }

        for (String consumerName : inboxHandlerRegistry.consumerNames()) {
            processConsumerMessages(consumerName, "poll");
        }
    }

    private void processConsumerMessages(String consumerName, String trigger) {
        if (consumerName == null || consumerName.isBlank()) {
            return;
        }

        AtomicBoolean runGuard = consumerRunGuards.computeIfAbsent(consumerName, key -> new AtomicBoolean(false));
        if (!runGuard.compareAndSet(false, true)) {
            return;
        }

        try {
            recoverStaleProcessingMessages(consumerName);
            int maxDrainLoops = Math.max(1, inboxProperties.getWorker().getMaxDrainLoops());
            for (int loop = 0; loop < maxDrainLoops; loop++) {
                List<InboxMessage> claimedMessages = claimDueMessages(consumerName);
                if (claimedMessages.isEmpty()) {
                    return;
                }

                for (InboxMessage message : claimedMessages) {
                    processClaimedMessage(message);
                }

                int batchSize = Math.max(1, inboxProperties.getWorker().getBatchSize());
                if (claimedMessages.size() < batchSize) {
                    return;
                }
            }

            log.debug("[InboxWorker] drain-loop limit reached. consumerName={}, trigger={}", consumerName, trigger);
        } finally {
            runGuard.set(false);
        }
    }

    private List<InboxMessage> claimDueMessages(String consumerName) {
        LocalDateTime now = LocalDateTime.now();
        int batchSize = Math.max(1, inboxProperties.getWorker().getBatchSize());

        List<Long> candidateIds = transactionTemplate.execute(status ->
                inboxRepository.findDueMessages(
                                consumerName,
                                InboxStatus.PENDING,
                                now,
                                PageRequest.of(0, batchSize)
                        )
                        .stream()
                        .map(InboxMessage::getId)
                        .toList()
        );

        if (candidateIds == null || candidateIds.isEmpty()) {
            return List.of();
        }

        LocalDateTime leaseUntil = now.plusSeconds(Math.max(1, inboxProperties.getWorker().getLeaseSeconds()));
        List<Long> claimedIds = transactionTemplate.execute(status -> {
            List<Long> ids = new ArrayList<>();
            for (Long candidateId : candidateIds) {
                int updatedRows = inboxRepository.claimDueMessage(
                        candidateId,
                        InboxStatus.PENDING,
                        InboxStatus.PROCESSING,
                        leaseUntil,
                        now
                );
                if (updatedRows > 0) {
                    ids.add(candidateId);
                }
            }
            return ids;
        });

        if (claimedIds == null || claimedIds.isEmpty()) {
            return List.of();
        }

        List<InboxMessage> claimedMessages = transactionTemplate.execute(status -> inboxRepository.findAllById(claimedIds));
        if (claimedMessages == null || claimedMessages.isEmpty()) {
            return List.of();
        }

        return claimedMessages.stream()
                .sorted(Comparator.comparing(InboxMessage::getCreatedAt))
                .toList();
    }

    private void recoverStaleProcessingMessages(String consumerName) {
        LocalDateTime now = LocalDateTime.now();
        int batchSize = Math.max(1, inboxProperties.getWorker().getStaleRecoveryBatchSize());

        List<Long> staleMessageIds = transactionTemplate.execute(status ->
                inboxRepository.findStaleProcessingMessages(
                                consumerName,
                                InboxStatus.PROCESSING,
                                now,
                                PageRequest.of(0, batchSize)
                        )
                        .stream()
                        .filter(message -> message.isProcessingLeaseExpired(now))
                        .map(InboxMessage::getId)
                        .toList()
        );

        if (staleMessageIds == null || staleMessageIds.isEmpty()) {
            return;
        }

        for (Long staleMessageId : staleMessageIds) {
            markFailure(staleMessageId, "processing lease expired");
        }

        log.warn("[InboxWorker] stale processing recovered. consumerName={}, count={}",
                consumerName, staleMessageIds.size());
    }

    private void processClaimedMessage(InboxMessage message) {
        Optional<InboxEventHandler> optionalHandler = inboxHandlerRegistry.findHandler(
                message.getConsumerName(), message.getEventType());
        if (optionalHandler.isEmpty()) {
            markDead(message.getId(), "unsupported eventType: " + message.getEventType());
            return;
        }

        InboxEventHandler handler = optionalHandler.get();
        try {
            transactionTemplate.executeWithoutResult(status -> inboxRepository.findById(message.getId()).ifPresent(current -> {
                if (current.getStatus() != InboxStatus.PROCESSING) {
                    return;
                }
                try {
                    handler.handle(current.getEventId(), current.getEventType(), current.getPayload());
                    current.markSucceeded(LocalDateTime.now());
                    inboxRepository.save(current);
                } catch (Exception exception) {
                    throw new InboxProcessingException(exception);
                }
            }));
        } catch (InboxProcessingException exception) {
            markFailure(message.getId(), exception.getCause() == null
                    ? exception.getMessage()
                    : exception.getCause().getMessage());
        } catch (Exception exception) {
            markFailure(message.getId(), exception.getMessage());
        }
    }

    private void markFailure(Long messageId, String reason) {
        transactionTemplate.executeWithoutResult(status -> inboxRepository.findById(messageId).ifPresent(message -> {
            if (message.getStatus() != InboxStatus.PROCESSING) {
                return;
            }

            int maxRetryCount = Math.max(0, inboxProperties.getWorker().getMaxRetryCount());
            int maxErrorLength = Math.max(32, inboxProperties.getWorker().getMaxErrorMessageLength());
            LocalDateTime now = LocalDateTime.now();
            if (message.exceedsRetryLimitOnNextFailure(maxRetryCount)) {
                message.markDead(now, reason, maxErrorLength);
            } else {
                long delaySeconds = computeBackoffSeconds(message.nextRetryCount());
                message.markRetry(now, now.plusSeconds(delaySeconds), reason, maxErrorLength);
            }
            inboxRepository.save(message);
        }));
    }

    private void markDead(Long messageId, String reason) {
        transactionTemplate.executeWithoutResult(status -> inboxRepository.findById(messageId).ifPresent(message -> {
            if (message.getStatus() != InboxStatus.PROCESSING && message.getStatus() != InboxStatus.PENDING) {
                return;
            }
            int maxErrorLength = Math.max(32, inboxProperties.getWorker().getMaxErrorMessageLength());
            message.markDead(LocalDateTime.now(), reason, maxErrorLength);
            inboxRepository.save(message);
        }));
    }

    private long computeBackoffSeconds(int nextRetryCount) {
        long baseDelaySeconds = Math.max(1, inboxProperties.getWorker().getBaseRetryDelaySeconds());
        long maxDelaySeconds = Math.max(baseDelaySeconds, inboxProperties.getWorker().getMaxRetryDelaySeconds());

        long delaySeconds = baseDelaySeconds;
        for (int i = 1; i < Math.max(1, nextRetryCount); i++) {
            if (delaySeconds >= maxDelaySeconds / 2) {
                return maxDelaySeconds;
            }
            delaySeconds = delaySeconds * 2;
        }
        return Math.min(delaySeconds, maxDelaySeconds);
    }

    private static class InboxProcessingException extends RuntimeException {
        InboxProcessingException(Throwable cause) {
            super(cause);
        }
    }
}
