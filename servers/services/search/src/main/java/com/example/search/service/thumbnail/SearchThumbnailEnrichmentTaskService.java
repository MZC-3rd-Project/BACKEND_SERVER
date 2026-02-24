package com.example.search.service.thumbnail;

import com.example.clients.media.facade.MediaClientFacade;
import com.example.search.entity.SearchThumbnailEnrichmentStatus;
import com.example.search.entity.SearchThumbnailEnrichmentTask;
import com.example.search.repository.SearchThumbnailEnrichmentTaskRepository;
import com.example.search.service.index.SearchIndexingService;
import com.example.search.service.query.cache.SearchResultCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchThumbnailEnrichmentTaskService {

    private final SearchThumbnailEnrichmentTaskRepository taskRepository;
    private final SearchIndexingService searchIndexingService;
    private final SearchResultCacheService searchResultCacheService;
    private final MediaClientFacade mediaClientFacade;
    private final TransactionTemplate transactionTemplate;

    @Value("${search.thumbnail-enricher.batch-size:50}")
    private int batchSize;

    @Value("${search.thumbnail-enricher.processing-stale-threshold-seconds:120}")
    private long processingStaleThresholdSeconds;

    @Value("${search.thumbnail-enricher.max-retry-count:7}")
    private int maxRetryCount;

    @Value("${search.thumbnail-enricher.base-delay-seconds:30}")
    private long baseDelaySeconds;

    @Value("${search.thumbnail-enricher.max-delay-seconds:600}")
    private long maxDelaySeconds;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(Long itemId, Long thumbnailMediaId, Long mediaVersion) {
        if (!isValidTaskInput(itemId, thumbnailMediaId, mediaVersion)) {
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            SearchThumbnailEnrichmentTask task = taskRepository.findByItemIdForUpdate(itemId).orElse(null);
            if (task == null) {
                taskRepository.save(SearchThumbnailEnrichmentTask.create(itemId, thumbnailMediaId, mediaVersion));
                return;
            }
            task.upsertPending(thumbnailMediaId, mediaVersion);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removeTask(Long itemId) {
        if (itemId == null || itemId <= 0) {
            return;
        }

        transactionTemplate.executeWithoutResult(status ->
                taskRepository.findByItemIdForUpdate(itemId).ifPresent(SearchThumbnailEnrichmentTask::softDelete)
        );
    }

    public void processDueRetries() {
        recoverStaleProcessingTasks();

        List<TaskSnapshot> snapshots = claimDueTasks();
        if (snapshots.isEmpty()) {
            return;
        }

        Map<Long, String> mediaUrlMap;
        try {
            mediaUrlMap = mediaClientFacade.getMediaUrlMap(
                    snapshots.stream().map(TaskSnapshot::thumbnailMediaId).distinct().toList()
            );
        } catch (Exception e) {
            for (TaskSnapshot snapshot : snapshots) {
                scheduleRetryOrFail(snapshot.taskId(), e.getMessage());
            }
            log.warn("[SearchThumbnailEnricher] media batch call failed. taskCount={}", snapshots.size(), e);
            return;
        }

        for (TaskSnapshot snapshot : snapshots) {
            processOne(snapshot, mediaUrlMap.get(snapshot.thumbnailMediaId()));
        }
    }

    private List<TaskSnapshot> claimDueTasks() {
        List<Long> dueTaskIds = transactionTemplate.execute(status ->
                taskRepository.findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                                SearchThumbnailEnrichmentStatus.PENDING,
                                LocalDateTime.now()
                        ).stream()
                        .limit(Math.max(1, batchSize))
                        .map(SearchThumbnailEnrichmentTask::getId)
                        .toList()
        );
        if (dueTaskIds == null || dueTaskIds.isEmpty()) {
            return List.of();
        }

        List<TaskSnapshot> claimed = new ArrayList<>();
        for (Long taskId : dueTaskIds) {
            boolean claimedNow = Boolean.TRUE.equals(transactionTemplate.execute(status ->
                    taskRepository.claimForProcessing(
                            taskId,
                            SearchThumbnailEnrichmentStatus.PENDING,
                            SearchThumbnailEnrichmentStatus.PROCESSING,
                            LocalDateTime.now()
                    ) > 0
            ));
            if (!claimedNow) {
                continue;
            }

            TaskSnapshot snapshot = transactionTemplate.execute(status ->
                    taskRepository.findById(taskId).map(TaskSnapshot::from).orElse(null)
            );
            if (snapshot != null) {
                claimed.add(snapshot);
            }
        }
        return claimed;
    }

    private void processOne(TaskSnapshot snapshot, String mediaUrl) {
        if (!StringUtils.hasText(mediaUrl)) {
            scheduleRetryOrFail(snapshot.taskId(), "media url snapshot missing");
            return;
        }

        try {
            searchIndexingService.updateThumbnailSnapshot(
                    snapshot.itemId(),
                    snapshot.thumbnailMediaId(),
                    mediaUrl,
                    snapshot.mediaVersion()
            );

            Boolean completed = transactionTemplate.execute(status ->
                    taskRepository.findById(snapshot.taskId())
                            .map(current -> {
                                if (!current.isProcessing()) {
                                    return false;
                                }
                                if (!Objects.equals(current.getThumbnailMediaId(), snapshot.thumbnailMediaId())
                                        || !Objects.equals(current.getMediaVersion(), snapshot.mediaVersion())) {
                                    return false;
                                }
                                current.markCompleted();
                                return true;
                            })
                            .orElse(false)
            );
            if (Boolean.TRUE.equals(completed)) {
                searchResultCacheService.evictAll();
            }
        } catch (Exception e) {
            scheduleRetryOrFail(snapshot.taskId(), e.getMessage());
            log.warn("[SearchThumbnailEnricher] process failed. taskId={}, itemId={}, mediaId={}",
                    snapshot.taskId(), snapshot.itemId(), snapshot.thumbnailMediaId(), e);
        }
    }

    private void recoverStaleProcessingTasks() {
        LocalDateTime staleBefore = LocalDateTime.now().minusSeconds(Math.max(1, processingStaleThresholdSeconds));
        List<Long> staleIds = transactionTemplate.execute(status ->
                taskRepository.findTop100ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
                                SearchThumbnailEnrichmentStatus.PROCESSING,
                                staleBefore
                        ).stream()
                        .limit(Math.max(1, batchSize))
                        .map(SearchThumbnailEnrichmentTask::getId)
                        .toList()
        );
        if (staleIds == null || staleIds.isEmpty()) {
            return;
        }

        for (Long staleId : staleIds) {
            transactionTemplate.executeWithoutResult(status ->
                    taskRepository.findById(staleId).ifPresent(task -> {
                        if (!task.isProcessing()) {
                            return;
                        }
                        int nextRetryCount = task.getRetryCount() + 1;
                        if (nextRetryCount >= maxRetryCount) {
                            task.markFailed("Recovered stale PROCESSING and exceeded max retries");
                        } else {
                            task.scheduleNextRetry(
                                    "Recovered stale PROCESSING task",
                                    computeDelaySeconds(nextRetryCount)
                            );
                        }
                    })
            );
        }
    }

    private void scheduleRetryOrFail(Long taskId, String errorMessage) {
        transactionTemplate.executeWithoutResult(status ->
                taskRepository.findById(taskId).ifPresent(task -> {
                    if (!task.isProcessing()) {
                        return;
                    }
                    int nextRetryCount = task.getRetryCount() + 1;
                    if (nextRetryCount >= maxRetryCount) {
                        task.markFailed(errorMessage);
                    } else {
                        task.scheduleNextRetry(errorMessage, computeDelaySeconds(nextRetryCount));
                    }
                })
        );
    }

    private boolean isValidTaskInput(Long itemId, Long thumbnailMediaId, Long mediaVersion) {
        return itemId != null && itemId > 0
                && thumbnailMediaId != null && thumbnailMediaId > 0
                && mediaVersion != null && mediaVersion > 0;
    }

    private long computeDelaySeconds(int retryCount) {
        long multiplier = 1L << Math.max(0, retryCount - 1);
        long delaySeconds = Math.max(1, baseDelaySeconds) * multiplier;
        return Math.min(delaySeconds, Math.max(1, maxDelaySeconds));
    }

    private record TaskSnapshot(
            Long taskId,
            Long itemId,
            Long thumbnailMediaId,
            Long mediaVersion
    ) {
        private static TaskSnapshot from(SearchThumbnailEnrichmentTask task) {
            return new TaskSnapshot(
                    task.getId(),
                    task.getItemId(),
                    task.getThumbnailMediaId(),
                    task.getMediaVersion()
            );
        }
    }
}
