package com.example.review.service;

import com.example.review.entity.media.ReviewMediaLinkSyncStatus;
import com.example.review.entity.media.ReviewMediaLinkSyncTask;
import com.example.review.repository.ReviewMediaLinkSyncTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewMediaLinkSyncRetryService {

    private final ReviewMediaLinkSyncTaskRepository retryRepository;
    private final ReviewMediaService reviewMediaService;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.media-link-sync-retry.processing-stale-threshold-seconds:120}")
    private long processingStaleThresholdSeconds;

    @Value("${app.media-link-sync-retry.max-retry-count:7}")
    private int maxRetryCount;

    @Value("${app.media-link-sync-retry.base-delay-seconds:30}")
    private long baseDelaySeconds;

    @Value("${app.media-link-sync-retry.max-delay-seconds:600}")
    private long maxDelaySeconds;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(Long reviewId, List<Long> mediaIds, String errorMessage) {
        if (reviewId == null || reviewId <= 0 || mediaIds == null || mediaIds.isEmpty()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            ReviewMediaLinkSyncTask task = retryRepository.findByReviewIdForUpdate(reviewId).orElse(null);
            if (task == null) {
                retryRepository.save(ReviewMediaLinkSyncTask.create(reviewId, mediaIds, errorMessage));
                return;
            }
            task.upsertPending(mediaIds, errorMessage);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(Long reviewId, List<Long> mediaIds) {
        if (reviewId == null || reviewId <= 0 || mediaIds == null || mediaIds.isEmpty()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            ReviewMediaLinkSyncTask task = retryRepository.findByReviewIdForUpdate(reviewId).orElse(null);
            if (task == null) {
                return;
            }
            task.markCompleted(mediaIds);
        });
    }

    public void processDueRetries() {
        recoverStaleProcessingTasks();

        List<Long> taskIds = transactionTemplate.execute(status ->
                retryRepository.findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                                ReviewMediaLinkSyncStatus.PENDING,
                                LocalDateTime.now()
                        ).stream()
                        .map(ReviewMediaLinkSyncTask::getId)
                        .toList()
        );

        if (taskIds == null || taskIds.isEmpty()) {
            return;
        }

        for (Long taskId : taskIds) {
            processOneRetry(taskId);
        }
    }

    private void recoverStaleProcessingTasks() {
        LocalDateTime staleBefore = LocalDateTime.now().minusSeconds(processingStaleThresholdSeconds);
        List<Long> staleIds = transactionTemplate.execute(status ->
                retryRepository.findTop100ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
                                ReviewMediaLinkSyncStatus.PROCESSING,
                                staleBefore
                        ).stream()
                        .map(ReviewMediaLinkSyncTask::getId)
                        .toList()
        );

        if (staleIds == null || staleIds.isEmpty()) {
            return;
        }

        for (Long staleId : staleIds) {
            recoverStaleProcessingTask(staleId);
        }
    }

    private void recoverStaleProcessingTask(Long taskId) {
        transactionTemplate.executeWithoutResult(status ->
                retryRepository.findById(taskId).ifPresent(task -> {
                    if (!task.isProcessing()) {
                        return;
                    }

                    int nextRetryCount = task.getRetryCount() + 1;
                    if (nextRetryCount >= maxRetryCount) {
                        task.markFailed("Recovered stale PROCESSING and exceeded max retries");
                        log.error("Review media sync retry moved stale PROCESSING to FAILED: taskId={}, reviewId={}, retry={}",
                                taskId, task.getReviewId(), task.getRetryCount());
                    } else {
                        task.scheduleNextRetry("Recovered stale PROCESSING task", computeDelaySeconds(nextRetryCount));
                        log.warn("Review media sync retry recovered stale PROCESSING to PENDING: taskId={}, reviewId={}, retry={}",
                                taskId, task.getReviewId(), task.getRetryCount());
                    }
                })
        );
    }

    private void processOneRetry(Long taskId) {
        boolean claimed = Boolean.TRUE.equals(transactionTemplate.execute(status ->
                retryRepository.claimForProcessing(
                        taskId,
                        ReviewMediaLinkSyncStatus.PENDING,
                        ReviewMediaLinkSyncStatus.PROCESSING,
                        LocalDateTime.now()
                ) > 0
        ));
        if (!claimed) {
            return;
        }

        ReviewMediaLinkSyncTask task = transactionTemplate.execute(status ->
                retryRepository.findById(taskId).orElse(null));
        if (task == null) {
            return;
        }

        try {
            reviewMediaService.syncReviewImages(task.getReviewId(), task.getGalleryMediaIdList());

            transactionTemplate.executeWithoutResult(status ->
                    retryRepository.findById(taskId).ifPresent(current -> {
                        if (current.isProcessing()) {
                            current.markCompleted(current.getGalleryMediaIdList());
                        }
                    })
            );

            log.info("Review media sync retry succeeded: taskId={}, reviewId={}, retryCount={}",
                    taskId, task.getReviewId(), task.getRetryCount());
        } catch (Exception e) {
            transactionTemplate.executeWithoutResult(status ->
                    retryRepository.findById(taskId).ifPresent(current -> {
                        if (!current.isProcessing()) {
                            return;
                        }

                        int nextRetryCount = current.getRetryCount() + 1;
                        if (nextRetryCount >= maxRetryCount) {
                            current.markFailed(e.getMessage());
                        } else {
                            current.scheduleNextRetry(e.getMessage(), computeDelaySeconds(nextRetryCount));
                        }
                    })
            );

            log.warn("Review media sync retry failed: taskId={}, reviewId={}, error={}",
                    taskId, task.getReviewId(), e.getMessage());
        }
    }

    private long computeDelaySeconds(int retryCount) {
        long multiplier = 1L << Math.max(0, retryCount - 1);
        long delaySeconds = baseDelaySeconds * multiplier;
        return Math.min(delaySeconds, maxDelaySeconds);
    }
}
