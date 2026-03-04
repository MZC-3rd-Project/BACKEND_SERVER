package com.example.mediaworker.service;

import com.example.mediaworker.config.MediaWorkerCleanupProperties;
import com.example.mediaworker.config.MediaWorkerS3Properties;
import com.example.mediaworker.entity.MediaDerivative;
import com.example.mediaworker.entity.MediaDerivativeStatus;
import com.example.mediaworker.repository.MediaDerivativeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaDerivativeCleanupService {

    private final MediaDerivativeRepository mediaDerivativeRepository;
    private final MediaWorkerCleanupProperties mediaWorkerCleanupProperties;
    private final MediaWorkerS3Properties mediaWorkerS3Properties;
    private final S3Client mediaWorkerS3Client;

    @Transactional
    public CleanupResult cleanupOrphanedDerivatives() {
        if (!mediaWorkerCleanupProperties.isEnabled()) {
            return CleanupResult.disabled();
        }

        int batchSize = Math.max(1, mediaWorkerCleanupProperties.getBatchSize());
        List<MediaDerivative> orphaned = mediaDerivativeRepository.findOrphanedDerivatives(
                MediaDerivativeStatus.READY,
                PageRequest.of(0, batchSize)
        );
        int purgedCount = 0;
        int deletedObjectCount = 0;
        int deleteFailedCount = 0;

        for (MediaDerivative derivative : orphaned) {
            if (mediaWorkerCleanupProperties.isDeleteObjectEnabled()) {
                if (!deleteObjectQuietly(derivative)) {
                    deleteFailedCount++;
                    continue;
                }
                deletedObjectCount++;
            }

            derivative.markDeleted();
            purgedCount++;
        }

        return new CleanupResult(orphaned.size(), purgedCount, deletedObjectCount, deleteFailedCount);
    }

    private boolean deleteObjectQuietly(MediaDerivative derivative) {
        try {
            mediaWorkerS3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(mediaWorkerS3Properties.getBucket())
                            .key(derivative.getObjectKey())
                            .build()
            );
            return true;
        } catch (Exception e) {
            log.warn(
                    "[MediaWorker][Cleanup] derivative object delete failed. derivativeId={}, mediaId={}, objectKey={}, reason={}",
                    derivative.getId(),
                    derivative.getMediaId(),
                    derivative.getObjectKey(),
                    e.getMessage()
            );
            return false;
        }
    }

    public record CleanupResult(
            int orphanCount,
            int purgedCount,
            int deletedObjectCount,
            int deleteFailedCount
    ) {
        static CleanupResult disabled() {
            return new CleanupResult(0, 0, 0, 0);
        }
    }
}
