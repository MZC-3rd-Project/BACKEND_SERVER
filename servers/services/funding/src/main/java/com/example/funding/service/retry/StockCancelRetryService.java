package com.example.funding.service.retry;

import com.example.funding.client.StockClient;
import com.example.funding.entity.StockCancelRetry;
import com.example.funding.entity.StockCancelRetryStatus;
import com.example.funding.repository.StockCancelRetryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockCancelRetryService {

    private static final int MAX_RETRY_COUNT = 5;
    private static final long BASE_DELAY_SECONDS = 30;
    private static final long MAX_DELAY_SECONDS = 600;
    private static final long PROCESSING_STALE_THRESHOLD_SECONDS = 120;

    private final StockCancelRetryRepository retryRepository;
    private final StockClient stockClient;
    private final TransactionTemplate transactionTemplate;

    public void enqueue(Long participationId, Long reservationId, String errorMessage) {
        transactionTemplate.executeWithoutResult(status ->
                retryRepository.save(StockCancelRetry.create(participationId, reservationId, errorMessage)));
    }

    public void processDueRetries() {
        recoverStaleProcessingTasks();

        List<Long> retryIds = transactionTemplate.execute(status ->
                retryRepository.findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                                StockCancelRetryStatus.PENDING, LocalDateTime.now())
                        .stream()
                        .map(StockCancelRetry::getId)
                        .toList()
        );

        if (retryIds == null || retryIds.isEmpty()) {
            return;
        }

        for (Long retryId : retryIds) {
            processOneRetry(retryId);
        }
    }

    private void recoverStaleProcessingTasks() {
        LocalDateTime staleBefore = LocalDateTime.now().minusSeconds(PROCESSING_STALE_THRESHOLD_SECONDS);
        List<Long> staleIds = transactionTemplate.execute(status ->
                retryRepository.findTop100ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
                                StockCancelRetryStatus.PROCESSING, staleBefore)
                        .stream()
                        .map(StockCancelRetry::getId)
                        .toList()
        );

        if (staleIds == null || staleIds.isEmpty()) {
            return;
        }

        for (Long staleId : staleIds) {
            recoverStaleProcessingTask(staleId);
        }
    }

    private void recoverStaleProcessingTask(Long retryId) {
        transactionTemplate.executeWithoutResult(status ->
                retryRepository.findById(retryId).ifPresent(task -> {
                    if (task.getStatus() != StockCancelRetryStatus.PROCESSING) {
                        return;
                    }

                    int nextRetryCount = task.getRetryCount() + 1;
                    if (nextRetryCount >= MAX_RETRY_COUNT) {
                        task.markFailed("Recovered stale PROCESSING and exceeded max retries");
                        log.error("Funding retry moved stale PROCESSING to FAILED: retryId={}, reservationId={}, retry={}",
                                retryId, task.getReservationId(), task.getRetryCount());
                    } else {
                        task.scheduleNextRetry("Recovered stale PROCESSING task", computeDelaySeconds(nextRetryCount));
                        log.warn("Funding retry recovered stale PROCESSING to PENDING: retryId={}, reservationId={}, retry={}",
                                retryId, task.getReservationId(), task.getRetryCount());
                    }
                })
        );
    }

    private void processOneRetry(Long retryId) {
        boolean claimed = Boolean.TRUE.equals(transactionTemplate.execute(status ->
                retryRepository.claimForProcessing(
                        retryId,
                        StockCancelRetryStatus.PENDING,
                        StockCancelRetryStatus.PROCESSING,
                        LocalDateTime.now()
                ) > 0
        ));

        if (!claimed) {
            return;
        }

        StockCancelRetry retryTask = transactionTemplate.execute(status ->
                retryRepository.findById(retryId).orElse(null));
        if (retryTask == null) {
            return;
        }

        try {
            stockClient.cancelReservation(retryTask.getReservationId());

            transactionTemplate.executeWithoutResult(status ->
                    retryRepository.findById(retryId).ifPresent(task -> {
                        if (task.getStatus() == StockCancelRetryStatus.PROCESSING) {
                            task.markCompleted();
                        }
                    })
            );

            log.info("Stock cancel retry succeeded: retryId={}, participationId={}, reservationId={}, retryCount={}",
                    retryId, retryTask.getParticipationId(), retryTask.getReservationId(), retryTask.getRetryCount());
        } catch (Exception e) {
            transactionTemplate.executeWithoutResult(status ->
                    retryRepository.findById(retryId).ifPresent(task -> {
                        if (task.getStatus() != StockCancelRetryStatus.PROCESSING) {
                            return;
                        }

                        int nextRetryCount = task.getRetryCount() + 1;
                        if (nextRetryCount >= MAX_RETRY_COUNT) {
                            task.markFailed(e.getMessage());
                        } else {
                            task.scheduleNextRetry(e.getMessage(), computeDelaySeconds(nextRetryCount));
                        }
                    })
            );

            log.warn("Stock cancel retry failed: retryId={}, reservationId={}, error={}",
                    retryId, retryTask.getReservationId(), e.getMessage());
        }
    }

    private long computeDelaySeconds(int retryCount) {
        long delaySeconds = BASE_DELAY_SECONDS * (1L << Math.max(0, retryCount - 1));
        return Math.min(delaySeconds, MAX_DELAY_SECONDS);
    }
}
