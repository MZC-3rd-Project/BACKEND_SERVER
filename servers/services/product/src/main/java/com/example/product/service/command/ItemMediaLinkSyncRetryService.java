package com.example.product.service.command;

import com.example.product.entity.image.ItemMediaLinkSyncStatus;
import com.example.product.entity.image.ItemMediaLinkSyncTask;
import com.example.product.repository.ItemMediaLinkSyncTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemMediaLinkSyncRetryService {

    private final ItemMediaLinkSyncTaskRepository retryRepository;
    private final MediaReferenceService mediaReferenceService;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.media-link-sync-retry.processing-stale-threshold-seconds:120}")
    private long processingStaleThresholdSeconds;

    @Value("${app.media-link-sync-retry.max-retry-count:7}")
    private int maxRetryCount;

    @Value("${app.media-link-sync-retry.base-delay-seconds:30}")
    private long baseDelaySeconds;

    @Value("${app.media-link-sync-retry.max-delay-seconds:600}")
    private long maxDelaySeconds;

    public void enqueue(Long itemId, Long thumbnailMediaId, List<Long> galleryMediaIds, String errorMessage) {
        if (itemId == null || itemId <= 0) {
            return;
        }
        List<Long> safeGalleryMediaIds = galleryMediaIds == null ? List.of() : galleryMediaIds;
        transactionTemplate.executeWithoutResult(status -> {
            ItemMediaLinkSyncTask task = retryRepository.findByItemIdForUpdate(itemId).orElse(null);
            if (task == null) {
                retryRepository.save(ItemMediaLinkSyncTask.create(itemId, thumbnailMediaId, safeGalleryMediaIds, errorMessage));
                return;
            }
            task.upsertPending(thumbnailMediaId, safeGalleryMediaIds, errorMessage);
        });
    }

    public void markCompleted(Long itemId, Long thumbnailMediaId, List<Long> galleryMediaIds) {
        if (itemId == null || itemId <= 0) {
            return;
        }
        List<Long> safeGalleryMediaIds = galleryMediaIds == null ? List.of() : galleryMediaIds;
        transactionTemplate.executeWithoutResult(status -> {
            ItemMediaLinkSyncTask task = retryRepository.findByItemIdForUpdate(itemId).orElse(null);
            if (task == null) {
                return;
            }
            task.markCompleted(thumbnailMediaId, safeGalleryMediaIds);
        });
    }

    public void processDueRetries() {
        recoverStaleProcessingTasks();

        List<Long> taskIds = transactionTemplate.execute(status ->
                retryRepository.findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                                ItemMediaLinkSyncStatus.PENDING,
                                LocalDateTime.now()
                        ).stream()
                        .map(ItemMediaLinkSyncTask::getId)
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
                                ItemMediaLinkSyncStatus.PROCESSING,
                                staleBefore
                        ).stream()
                        .map(ItemMediaLinkSyncTask::getId)
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
                        log.error("Item media sync retry moved stale PROCESSING to FAILED: taskId={}, itemId={}, retry={}",
                                taskId, task.getItemId(), task.getRetryCount());
                    } else {
                        task.scheduleNextRetry("Recovered stale PROCESSING task", computeDelaySeconds(nextRetryCount));
                        log.warn("Item media sync retry recovered stale PROCESSING to PENDING: taskId={}, itemId={}, retry={}",
                                taskId, task.getItemId(), task.getRetryCount());
                    }
                })
        );
    }

    private void processOneRetry(Long taskId) {
        boolean claimed = Boolean.TRUE.equals(transactionTemplate.execute(status ->
                retryRepository.claimForProcessing(
                        taskId,
                        ItemMediaLinkSyncStatus.PENDING,
                        ItemMediaLinkSyncStatus.PROCESSING,
                        LocalDateTime.now()
                ) > 0
        ));
        if (!claimed) {
            return;
        }

        ItemMediaLinkSyncTask task = transactionTemplate.execute(status ->
                retryRepository.findById(taskId).orElse(null));
        if (task == null) {
            return;
        }

        try {
            mediaReferenceService.syncItemMediaLinks(
                    task.getItemId(),
                    task.getThumbnailMediaId(),
                    task.getGalleryMediaIdList()
            );

            transactionTemplate.executeWithoutResult(status ->
                    retryRepository.findById(taskId).ifPresent(current -> {
                        if (current.isProcessing()) {
                            current.markCompleted(current.getThumbnailMediaId(), current.getGalleryMediaIdList());
                        }
                    })
            );

            log.info("Item media sync retry succeeded: taskId={}, itemId={}, retryCount={}",
                    taskId, task.getItemId(), task.getRetryCount());
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

            log.warn("Item media sync retry failed: taskId={}, itemId={}, error={}",
                    taskId, task.getItemId(), e.getMessage());
        }
    }

    private long computeDelaySeconds(int retryCount) {
        long multiplier = 1L << Math.max(0, retryCount - 1);
        long delaySeconds = baseDelaySeconds * multiplier;
        return Math.min(delaySeconds, maxDelaySeconds);
    }
}
