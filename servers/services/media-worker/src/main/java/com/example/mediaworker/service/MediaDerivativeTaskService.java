package com.example.mediaworker.service;

import com.example.mediaworker.config.MediaWorkerTaskProperties;
import com.example.mediaworker.entity.MediaDerivativeProfile;
import com.example.mediaworker.entity.MediaDerivativeTask;
import com.example.mediaworker.entity.MediaDerivativeTaskStatus;
import com.example.mediaworker.entity.MediaDerivativeTaskDlq;
import com.example.mediaworker.repository.MediaDerivativeTaskRepository;
import com.example.mediaworker.repository.MediaDerivativeTaskDlqRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaDerivativeTaskService {

    private static final long MIN_MEDIA_VERSION = 1L;
    private static final String STALE_PROCESSING_ERROR = "stale processing timeout";

    private final MediaDerivativeTaskRepository mediaDerivativeTaskRepository;
    private final MediaDerivativeTaskDlqRepository mediaDerivativeTaskDlqRepository;
    private final MediaWorkerTaskProperties mediaWorkerTaskProperties;
    private final MediaDerivativeFailureClassifier failureClassifier;

    @Transactional
    public MediaDerivativeTask enqueuePending(Long mediaId,
                                              Long mediaVersion,
                                              MediaDerivativeProfile derivativeProfile,
                                              String sourceEventId) {
        if (mediaId == null || mediaId <= 0) {
            throw new IllegalArgumentException("mediaId must be positive");
        }
        if (derivativeProfile == null) {
            throw new IllegalArgumentException("derivativeProfile is required");
        }
        long normalizedVersion = normalizeMediaVersion(mediaVersion);

        Optional<MediaDerivativeTask> existing = mediaDerivativeTaskRepository
                .findByMediaIdAndDerivativeProfileAndMediaVersion(mediaId, derivativeProfile, normalizedVersion);
        if (existing.isPresent()) {
            log.debug("[MediaWorker] derivative task reused. taskId={}, mediaId={}, profile={}, version={}",
                    existing.get().getId(), mediaId, derivativeProfile, normalizedVersion);
            return existing.get();
        }

        MediaDerivativeTask pendingTask = MediaDerivativeTask.createPending(
                mediaId,
                derivativeProfile,
                normalizedVersion,
                sourceEventId
        );
        try {
            MediaDerivativeTask saved = mediaDerivativeTaskRepository.save(pendingTask);
            log.info("[MediaWorker] derivative task enqueued. taskId={}, mediaId={}, profile={}, version={}",
                    saved.getId(), mediaId, derivativeProfile, normalizedVersion);
            return saved;
        } catch (DataIntegrityViolationException duplicate) {
            // 동일 키(mediaId + profile + version) 동시 생성 경쟁 시 재조회로 멱등 처리한다.
            MediaDerivativeTask reused = mediaDerivativeTaskRepository.findByMediaIdAndDerivativeProfileAndMediaVersion(
                            mediaId,
                            derivativeProfile,
                            normalizedVersion
                    )
                    .orElseThrow(() -> duplicate);
            log.debug("[MediaWorker] derivative task reused after concurrent enqueue. taskId={}, mediaId={}, profile={}, version={}",
                    reused.getId(), mediaId, derivativeProfile, normalizedVersion);
            return reused;
        }
    }

    @Transactional(readOnly = true)
    public Optional<MediaDerivativeTask> findById(Long taskId) {
        return mediaDerivativeTaskRepository.findById(taskId);
    }

    @Transactional
    public List<Long> claimDueTaskIds(LocalDateTime now) {
        int batchSize = Math.max(1, mediaWorkerTaskProperties.getBatchSize());
        List<MediaDerivativeTask> dueTasks = mediaDerivativeTaskRepository.findDueTasks(
                MediaDerivativeTaskStatus.PENDING,
                now,
                PageRequest.of(0, batchSize)
        );

        List<Long> claimedTaskIds = new ArrayList<>();
        for (MediaDerivativeTask dueTask : dueTasks) {
            int updatedRows = mediaDerivativeTaskRepository.claimDueTask(
                    dueTask.getId(),
                    MediaDerivativeTaskStatus.PENDING,
                    MediaDerivativeTaskStatus.PROCESSING,
                    now,
                    now
            );
            if (updatedRows == 1) {
                claimedTaskIds.add(dueTask.getId());
            }
        }
        return claimedTaskIds;
    }

    @Transactional
    public int recoverStaleProcessingTasks(LocalDateTime now) {
        int batchSize = Math.max(1, mediaWorkerTaskProperties.getBatchSize());
        LocalDateTime staleBefore = now.minusSeconds(Math.max(1L, mediaWorkerTaskProperties.getStaleProcessingSeconds()));

        List<MediaDerivativeTask> staleTasks = mediaDerivativeTaskRepository.findStaleProcessingTasks(
                MediaDerivativeTaskStatus.PROCESSING,
                staleBefore,
                PageRequest.of(0, batchSize)
        );

        int recoveredCount = 0;
        for (MediaDerivativeTask staleTask : staleTasks) {
            int updatedRows = mediaDerivativeTaskRepository.recoverStaleTask(
                    staleTask.getId(),
                    MediaDerivativeTaskStatus.PROCESSING,
                    MediaDerivativeTaskStatus.PENDING,
                    now,
                    staleBefore,
                    STALE_PROCESSING_ERROR
            );
            if (updatedRows == 1) {
                recoveredCount++;
            }
        }
        return recoveredCount;
    }

    @Transactional
    public void markCompleted(Long taskId, LocalDateTime completedAt) {
        mediaDerivativeTaskRepository.findById(taskId).ifPresent(task -> {
            if (task.getStatus() == MediaDerivativeTaskStatus.COMPLETED) {
                return;
            }
            if (task.getStatus() != MediaDerivativeTaskStatus.PROCESSING) {
                log.warn(
                        "[MediaWorker] skip complete. taskId={}, status={}",
                        task.getId(),
                        task.getStatus()
                );
                return;
            }
            task.markCompleted(completedAt);
            log.info("[MediaWorker] derivative task completed. taskId={}, mediaId={}, profile={}",
                    task.getId(), task.getMediaId(), task.getDerivativeProfile());
        });
    }

    @Transactional
    public MediaDerivativeFailureHandleResult handleFailure(Long taskId, Exception exception, LocalDateTime now) {
        return mediaDerivativeTaskRepository.findById(taskId)
                .map(task -> handleFailure(task, exception, now))
                .orElseGet(MediaDerivativeFailureHandleResult::skipped);
    }

    @Transactional
    public Optional<MediaDerivativeTask> replayFailedTask(Long taskId, String replayReason, LocalDateTime replayAt) {
        return mediaDerivativeTaskRepository.findById(taskId).map(task -> {
            if (task.getStatus() != MediaDerivativeTaskStatus.FAILED) {
                return task;
            }
            task.requeueForReplay(replayAt, replayReason);
            return task;
        });
    }

    private MediaDerivativeFailureHandleResult handleFailure(MediaDerivativeTask task, Exception exception, LocalDateTime now) {
        if (task.getStatus() != MediaDerivativeTaskStatus.PROCESSING) {
            log.warn(
                    "[MediaWorker] skip failure handling. taskId={}, status={}",
                    task.getId(),
                    task.getStatus()
            );
            return MediaDerivativeFailureHandleResult.skipped();
        }

        String errorMessage = buildErrorMessage(exception);
        int nextRetryCount = task.getRetryCount() == null ? 1 : task.getRetryCount() + 1;
        MediaDerivativeFailureDecision decision = failureClassifier.classify(
                exception,
                nextRetryCount,
                mediaWorkerTaskProperties.getMaxRetryCount()
        );
        if (!decision.retriable()) {
            task.markFailed(errorMessage, now);
            mediaDerivativeTaskDlqRepository.save(
                    MediaDerivativeTaskDlq.fromTask(task, decision.failureCode().name(), errorMessage)
            );
            log.error("[MediaWorker] derivative task moved to DLQ. taskId={}, mediaId={}, profile={}, failureCode={}",
                    task.getId(), task.getMediaId(), task.getDerivativeProfile(), decision.failureCode());
            return MediaDerivativeFailureHandleResult.dlqFailed(decision.failureCode(), task.getRetryCount());
        }

        long backoffSeconds = calculateBackoffSeconds(nextRetryCount);
        task.scheduleRetry(now.plusSeconds(backoffSeconds), errorMessage);
        log.warn("[MediaWorker] derivative task scheduled for retry. taskId={}, mediaId={}, profile={}, retryCount={}, backoffSeconds={}",
                task.getId(), task.getMediaId(), task.getDerivativeProfile(), task.getRetryCount(), backoffSeconds);
        return MediaDerivativeFailureHandleResult.retryScheduled(decision.failureCode(), task.getRetryCount());
    }

    private long normalizeMediaVersion(Long mediaVersion) {
        if (mediaVersion == null || mediaVersion < MIN_MEDIA_VERSION) {
            return MIN_MEDIA_VERSION;
        }
        return mediaVersion;
    }

    private String buildErrorMessage(Exception exception) {
        if (exception == null) {
            return "unknown worker exception";
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getClass().getSimpleName() + ": " + message;
    }

    private long calculateBackoffSeconds(int retryCount) {
        long initialDelay = Math.max(1L, mediaWorkerTaskProperties.getRetryInitialDelaySeconds());
        long maxDelay = Math.max(initialDelay, mediaWorkerTaskProperties.getRetryMaxDelaySeconds());
        long delay = initialDelay;

        for (int attempt = 1; attempt < retryCount; attempt++) {
            if (delay >= maxDelay) {
                return maxDelay;
            }
            if (delay > maxDelay / 2) {
                return maxDelay;
            }
            delay = delay * 2;
        }
        return Math.min(delay, maxDelay);
    }
}
