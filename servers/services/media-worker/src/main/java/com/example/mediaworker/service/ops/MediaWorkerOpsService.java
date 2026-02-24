package com.example.mediaworker.service.ops;

import com.example.mediaworker.dto.internal.ops.request.MediaWorkerBackfillRequest;
import com.example.mediaworker.dto.internal.ops.response.MediaWorkerBackfillResponse;
import com.example.mediaworker.dto.internal.ops.response.MediaWorkerDlqItemResponse;
import com.example.mediaworker.dto.internal.ops.response.MediaWorkerDlqListResponse;
import com.example.mediaworker.dto.internal.ops.response.MediaWorkerTaskSummaryResponse;
import com.example.mediaworker.entity.MediaDerivativeProfile;
import com.example.mediaworker.entity.MediaDerivativeTask;
import com.example.mediaworker.entity.MediaDerivativeTaskDlq;
import com.example.mediaworker.entity.MediaDerivativeTaskStatus;
import com.example.mediaworker.entity.MediaFileRecord;
import com.example.mediaworker.entity.MediaFileStatus;
import com.example.mediaworker.repository.MediaDerivativeTaskDlqRepository;
import com.example.mediaworker.repository.MediaDerivativeTaskRepository;
import com.example.mediaworker.repository.MediaFileRecordRepository;
import com.example.mediaworker.service.MediaDerivativeTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaWorkerOpsService {

    private static final int DEFAULT_DLQ_SIZE = 20;
    private static final int MAX_DLQ_SIZE = 200;
    private static final int DEFAULT_BACKFILL_SIZE = 100;
    private static final int MAX_BACKFILL_SIZE = 1_000;
    private static final long BACKFILL_MEDIA_VERSION = 1L;
    private static final String BACKFILL_EVENT_ID = "ops-backfill";

    private final MediaDerivativeTaskRepository mediaDerivativeTaskRepository;
    private final MediaDerivativeTaskDlqRepository mediaDerivativeTaskDlqRepository;
    private final MediaFileRecordRepository mediaFileRecordRepository;
    private final MediaDerivativeTaskService mediaDerivativeTaskService;

    @Transactional(readOnly = true)
    public MediaWorkerTaskSummaryResponse getTaskSummary() {
        return MediaWorkerTaskSummaryResponse.builder()
                .pendingCount(mediaDerivativeTaskRepository.countByStatus(MediaDerivativeTaskStatus.PENDING))
                .processingCount(mediaDerivativeTaskRepository.countByStatus(MediaDerivativeTaskStatus.PROCESSING))
                .completedCount(mediaDerivativeTaskRepository.countByStatus(MediaDerivativeTaskStatus.COMPLETED))
                .failedCount(mediaDerivativeTaskRepository.countByStatus(MediaDerivativeTaskStatus.FAILED))
                .dlqCount(mediaDerivativeTaskDlqRepository.count())
                .build();
    }

    @Transactional(readOnly = true)
    public MediaWorkerDlqListResponse getRecentDlqItems(Integer size) {
        int normalizedSize = normalizeSize(size, DEFAULT_DLQ_SIZE, MAX_DLQ_SIZE);
        List<MediaDerivativeTaskDlq> items = mediaDerivativeTaskDlqRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(0, normalizedSize)
        );
        return MediaWorkerDlqListResponse.builder()
                .size(items.size())
                .items(items.stream().map(this::toDlqItemResponse).toList())
                .build();
    }

    @Transactional
    public MediaDerivativeTask replayFailedTask(Long taskId, String reason) {
        if (taskId == null || taskId <= 0) {
            throw new IllegalArgumentException("taskId must be positive");
        }

        MediaDerivativeTask task = mediaDerivativeTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("task not found"));

        if (task.getStatus() != MediaDerivativeTaskStatus.FAILED) {
            throw new IllegalArgumentException("replay is only allowed for FAILED tasks");
        }

        MediaDerivativeTask replayed = mediaDerivativeTaskService
                .replayFailedTask(taskId, normalizeReplayReason(reason), LocalDateTime.now())
                .orElseThrow(() -> new IllegalArgumentException("task not found"));

        log.info(
                "[MediaWorkerOps] replay queued. taskId={}, mediaId={}, profile={}, mediaVersion={}, reason={}",
                replayed.getId(),
                replayed.getMediaId(),
                replayed.getDerivativeProfile(),
                replayed.getMediaVersion(),
                replayed.getLastError()
        );
        return replayed;
    }

    @Transactional
    public MediaWorkerBackfillResponse backfill(MediaWorkerBackfillRequest request) {
        Long fromMediaId = request == null ? null : request.getFromMediaId();
        Long toMediaId = request == null ? null : request.getToMediaId();
        int requestedSize = normalizeSize(request == null ? null : request.getSize(), DEFAULT_BACKFILL_SIZE, MAX_BACKFILL_SIZE);
        MediaDerivativeProfile profile = resolveProfile(request == null ? null : request.getDerivativeProfile());

        List<MediaFileRecord> candidates = mediaFileRecordRepository.findBackfillCandidates(
                List.of(MediaFileStatus.CONFIRMED, MediaFileStatus.READY),
                fromMediaId,
                toMediaId,
                PageRequest.of(0, requestedSize)
        );

        long queuedCount = 0L;
        long existingCount = 0L;

        for (MediaFileRecord candidate : candidates) {
            boolean alreadyExists = mediaDerivativeTaskRepository
                    .findByMediaIdAndDerivativeProfileAndMediaVersion(candidate.getId(), profile, BACKFILL_MEDIA_VERSION)
                    .isPresent();
            if (alreadyExists) {
                existingCount++;
                continue;
            }
            mediaDerivativeTaskService.enqueuePending(candidate.getId(), BACKFILL_MEDIA_VERSION, profile, BACKFILL_EVENT_ID);
            queuedCount++;
        }

        log.info(
                "[MediaWorkerOps] backfill finished. profile={}, fromMediaId={}, toMediaId={}, requestedSize={}, scanned={}, queued={}, existing={}",
                profile,
                fromMediaId,
                toMediaId,
                requestedSize,
                candidates.size(),
                queuedCount,
                existingCount
        );

        return MediaWorkerBackfillResponse.builder()
                .fromMediaId(fromMediaId)
                .toMediaId(toMediaId)
                .requestedSize(requestedSize)
                .derivativeProfile(profile.name())
                .scannedCount(candidates.size())
                .queuedCount(queuedCount)
                .existingCount(existingCount)
                .build();
    }

    private MediaWorkerDlqItemResponse toDlqItemResponse(MediaDerivativeTaskDlq item) {
        return MediaWorkerDlqItemResponse.builder()
                .dlqId(item.getId())
                .taskId(item.getTaskId())
                .mediaId(item.getMediaId())
                .derivativeProfile(item.getDerivativeProfile().name())
                .mediaVersion(item.getMediaVersion())
                .retryCount(item.getRetryCount())
                .errorCode(item.getErrorCode())
                .errorMessage(item.getErrorMessage())
                .sourceEventId(item.getSourceEventId())
                .createdAt(item.getCreatedAt())
                .build();
    }

    private int normalizeSize(Integer size, int defaultSize, int maxSize) {
        if (size == null || size <= 0) {
            return defaultSize;
        }
        return Math.min(size, maxSize);
    }

    private MediaDerivativeProfile resolveProfile(String rawProfile) {
        if (!StringUtils.hasText(rawProfile)) {
            return MediaDerivativeProfile.THUMBNAIL_WEBP;
        }
        String normalized = rawProfile.trim().toUpperCase(Locale.ROOT);
        try {
            return MediaDerivativeProfile.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return MediaDerivativeProfile.THUMBNAIL_WEBP;
        }
    }

    private String normalizeReplayReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return "manual replay requested";
        }
        return reason.trim();
    }
}
