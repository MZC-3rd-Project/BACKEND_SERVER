package com.example.mediaworker.service;

import com.example.mediaworker.config.MediaWorkerRawCleanupProperties;
import com.example.mediaworker.entity.MediaDerivative;
import com.example.mediaworker.entity.MediaDerivativeProfile;
import com.example.mediaworker.entity.MediaDerivativeStatus;
import com.example.mediaworker.entity.MediaFileRecord;
import com.example.mediaworker.entity.MediaFileStatus;
import com.example.mediaworker.repository.MediaDerivativeRepository;
import com.example.mediaworker.repository.MediaFileRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaRawCleanupService {

    private static final List<MediaFileStatus> RAW_DELETION_CANDIDATE_STATUSES =
            List.of(MediaFileStatus.READY);
    private static final int SCAN_MULTIPLIER = 5;

    private final MediaFileRecordRepository mediaFileRecordRepository;
    private final MediaDerivativeRepository mediaDerivativeRepository;
    private final MediaWorkerRawCleanupProperties mediaWorkerRawCleanupProperties;
    private final S3Client mediaWorkerS3Client;

    @Transactional
    public CleanupResult cleanupRawAfterTransition() {
        if (!mediaWorkerRawCleanupProperties.isEnabled()) {
            return CleanupResult.disabled();
        }

        int batchSize = Math.max(1, mediaWorkerRawCleanupProperties.getBatchSize());
        int scanLimit = Math.max(batchSize, batchSize * SCAN_MULTIPLIER);
        long graceDays = Math.max(0L, mediaWorkerRawCleanupProperties.getTransitionGraceDays());
        LocalDateTime eligibleBefore = LocalDateTime.now().minusDays(graceDays);

        List<MediaFileRecord> candidates = fetchCandidates(batchSize, scanLimit);

        int purgedCount = 0;
        int deletedObjectCount = 0;
        int deleteFailedCount = 0;
        int skippedDerivativeNotReadyCount = 0;
        int skippedGraceCount = 0;

        for (MediaFileRecord candidate : candidates) {
            if (purgedCount >= batchSize) {
                break;
            }
            Optional<MediaDerivative> thumbnail = findReadyDerivative(candidate.getId(), MediaDerivativeProfile.THUMBNAIL_WEBP);
            Optional<MediaDerivative> display = findReadyDerivative(candidate.getId(), MediaDerivativeProfile.DISPLAY_WEBP);
            if (thumbnail.isEmpty() || display.isEmpty()) {
                skippedDerivativeNotReadyCount++;
                continue;
            }

            LocalDateTime transitionReadyAt = latestReadyAt(thumbnail.get(), display.get());
            if (transitionReadyAt == null || transitionReadyAt.isAfter(eligibleBefore)) {
                skippedGraceCount++;
                continue;
            }

            if (mediaWorkerRawCleanupProperties.isDeleteObjectEnabled()) {
                if (!deleteRawObjectQuietly(candidate)) {
                    deleteFailedCount++;
                    continue;
                }
                deletedObjectCount++;
            }

            candidate.markDeleted();
            purgedCount++;
        }

        return new CleanupResult(
                candidates.size(),
                purgedCount,
                deletedObjectCount,
                deleteFailedCount,
                skippedDerivativeNotReadyCount,
                skippedGraceCount
        );
    }

    private List<MediaFileRecord> fetchCandidates(int pageSize, int scanLimit) {
        List<MediaFileRecord> collected = new ArrayList<>();
        int page = 0;
        while (collected.size() < scanLimit) {
            List<MediaFileRecord> pageCandidates = mediaFileRecordRepository.findRawDeletionCandidates(
                    RAW_DELETION_CANDIDATE_STATUSES,
                    PageRequest.of(page, pageSize)
            );
            if (pageCandidates.isEmpty()) {
                break;
            }
            collected.addAll(pageCandidates);
            if (pageCandidates.size() < pageSize) {
                break;
            }
            page++;
        }
        return collected.size() <= scanLimit ? collected : collected.subList(0, scanLimit);
    }

    private Optional<MediaDerivative> findReadyDerivative(Long mediaId, MediaDerivativeProfile profile) {
        return mediaDerivativeRepository.findTopByMediaIdAndDerivativeProfileAndStatusOrderByMediaVersionDescCreatedAtDesc(
                mediaId,
                profile,
                MediaDerivativeStatus.READY
        );
    }

    private LocalDateTime latestReadyAt(MediaDerivative first, MediaDerivative second) {
        LocalDateTime firstReadyAt = resolveReadyAt(first);
        LocalDateTime secondReadyAt = resolveReadyAt(second);
        if (firstReadyAt == null) {
            return secondReadyAt;
        }
        if (secondReadyAt == null) {
            return firstReadyAt;
        }
        return firstReadyAt.isAfter(secondReadyAt) ? firstReadyAt : secondReadyAt;
    }

    private LocalDateTime resolveReadyAt(MediaDerivative derivative) {
        if (derivative == null) {
            return null;
        }
        return derivative.getUpdatedAt() != null ? derivative.getUpdatedAt() : derivative.getCreatedAt();
    }

    private boolean deleteRawObjectQuietly(MediaFileRecord candidate) {
        try {
            mediaWorkerS3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(candidate.getBucketName())
                            .key(candidate.getObjectKey())
                            .build()
            );
            return true;
        } catch (Exception e) {
            log.warn(
                    "[MediaWorker][RawCleanup] raw object delete failed. mediaId={}, bucket={}, objectKey={}, reason={}",
                    candidate.getId(),
                    candidate.getBucketName(),
                    candidate.getObjectKey(),
                    e.getMessage()
            );
            return false;
        }
    }

    public record CleanupResult(
            int scannedCount,
            int purgedCount,
            int deletedObjectCount,
            int deleteFailedCount,
            int skippedDerivativeNotReadyCount,
            int skippedGraceCount
    ) {
        static CleanupResult disabled() {
            return new CleanupResult(0, 0, 0, 0, 0, 0);
        }
    }
}
