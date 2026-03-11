package com.example.mediaworker.service;

import com.example.mediaworker.config.MediaWorkerRawCleanupProperties;
import com.example.mediaworker.entity.MediaDerivative;
import com.example.mediaworker.entity.MediaDerivativeProfile;
import com.example.mediaworker.entity.MediaDerivativeStatus;
import com.example.mediaworker.entity.MediaFileRecord;
import com.example.mediaworker.entity.MediaFileStatus;
import com.example.mediaworker.repository.MediaDerivativeRepository;
import com.example.mediaworker.repository.MediaFileRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaRawCleanupServiceTest {

    @Mock
    private MediaFileRecordRepository mediaFileRecordRepository;

    @Mock
    private MediaDerivativeRepository mediaDerivativeRepository;

    @Mock
    private S3Client mediaWorkerS3Client;

    private MediaWorkerRawCleanupProperties rawCleanupProperties;
    private MediaRawCleanupService mediaRawCleanupService;

    @BeforeEach
    void setUp() {
        rawCleanupProperties = new MediaWorkerRawCleanupProperties();
        rawCleanupProperties.setEnabled(true);
        rawCleanupProperties.setBatchSize(10);
        rawCleanupProperties.setTransitionGraceDays(7);
        rawCleanupProperties.setDeleteObjectEnabled(false);

        mediaRawCleanupService = new MediaRawCleanupService(
                mediaFileRecordRepository,
                mediaDerivativeRepository,
                rawCleanupProperties,
                mediaWorkerS3Client
        );
    }

    @Test
    void cleanupRawAfterTransition_whenDerivativesNotReady_skipsCandidate() {
        MediaFileRecord candidate = createFileRecord(101L, MediaFileStatus.READY, "raw/101.jpg");
        when(mediaFileRecordRepository.findRawDeletionCandidates(any(), any(Pageable.class)))
                .thenReturn(List.of(candidate));
        when(mediaDerivativeRepository.findTopByMediaIdAndDerivativeProfileAndStatusOrderByMediaVersionDescCreatedAtDesc(
                101L,
                MediaDerivativeProfile.THUMBNAIL_WEBP,
                MediaDerivativeStatus.READY
        )).thenReturn(Optional.empty());
        when(mediaDerivativeRepository.findTopByMediaIdAndDerivativeProfileAndStatusOrderByMediaVersionDescCreatedAtDesc(
                101L,
                MediaDerivativeProfile.DISPLAY_WEBP,
                MediaDerivativeStatus.READY
        )).thenReturn(Optional.of(createDerivative(101L, MediaDerivativeProfile.DISPLAY_WEBP, LocalDateTime.now().minusDays(10))));

        MediaRawCleanupService.CleanupResult result = mediaRawCleanupService.cleanupRawAfterTransition();

        assertThat(result.scannedCount()).isEqualTo(1);
        assertThat(result.purgedCount()).isEqualTo(0);
        assertThat(result.skippedDerivativeNotReadyCount()).isEqualTo(1);
        assertThat(result.skippedGraceCount()).isEqualTo(0);
        assertThat(candidate.getStatus()).isEqualTo(MediaFileStatus.READY);
        assertThat(candidate.isDeleted()).isFalse();
    }

    @Test
    void cleanupRawAfterTransition_whenGraceWindowNotElapsed_skipsCandidate() {
        MediaFileRecord candidate = createFileRecord(102L, MediaFileStatus.READY, "raw/102.jpg");
        when(mediaFileRecordRepository.findRawDeletionCandidates(any(), any(Pageable.class)))
                .thenReturn(List.of(candidate));
        when(mediaDerivativeRepository.findTopByMediaIdAndDerivativeProfileAndStatusOrderByMediaVersionDescCreatedAtDesc(
                102L,
                MediaDerivativeProfile.THUMBNAIL_WEBP,
                MediaDerivativeStatus.READY
        )).thenReturn(Optional.of(createDerivative(102L, MediaDerivativeProfile.THUMBNAIL_WEBP, LocalDateTime.now().minusDays(2))));
        when(mediaDerivativeRepository.findTopByMediaIdAndDerivativeProfileAndStatusOrderByMediaVersionDescCreatedAtDesc(
                102L,
                MediaDerivativeProfile.DISPLAY_WEBP,
                MediaDerivativeStatus.READY
        )).thenReturn(Optional.of(createDerivative(102L, MediaDerivativeProfile.DISPLAY_WEBP, LocalDateTime.now().minusDays(1))));

        MediaRawCleanupService.CleanupResult result = mediaRawCleanupService.cleanupRawAfterTransition();

        assertThat(result.scannedCount()).isEqualTo(1);
        assertThat(result.purgedCount()).isEqualTo(0);
        assertThat(result.skippedDerivativeNotReadyCount()).isEqualTo(0);
        assertThat(result.skippedGraceCount()).isEqualTo(1);
        verify(mediaWorkerS3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void cleanupRawAfterTransition_whenEligibleAndDeleteEnabled_marksDeleted() {
        rawCleanupProperties.setDeleteObjectEnabled(true);
        MediaFileRecord candidate = createFileRecord(103L, MediaFileStatus.READY, "raw/103.jpg");
        when(mediaFileRecordRepository.findRawDeletionCandidates(any(), any(Pageable.class)))
                .thenReturn(List.of(candidate));
        when(mediaDerivativeRepository.findTopByMediaIdAndDerivativeProfileAndStatusOrderByMediaVersionDescCreatedAtDesc(
                103L,
                MediaDerivativeProfile.THUMBNAIL_WEBP,
                MediaDerivativeStatus.READY
        )).thenReturn(Optional.of(createDerivative(103L, MediaDerivativeProfile.THUMBNAIL_WEBP, LocalDateTime.now().minusDays(10))));
        when(mediaDerivativeRepository.findTopByMediaIdAndDerivativeProfileAndStatusOrderByMediaVersionDescCreatedAtDesc(
                103L,
                MediaDerivativeProfile.DISPLAY_WEBP,
                MediaDerivativeStatus.READY
        )).thenReturn(Optional.of(createDerivative(103L, MediaDerivativeProfile.DISPLAY_WEBP, LocalDateTime.now().minusDays(9))));

        MediaRawCleanupService.CleanupResult result = mediaRawCleanupService.cleanupRawAfterTransition();

        assertThat(result.scannedCount()).isEqualTo(1);
        assertThat(result.purgedCount()).isEqualTo(1);
        assertThat(result.deletedObjectCount()).isEqualTo(1);
        assertThat(result.deleteFailedCount()).isEqualTo(0);
        assertThat(candidate.getStatus()).isEqualTo(MediaFileStatus.DELETED);
        assertThat(candidate.isDeleted()).isTrue();
        verify(mediaWorkerS3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void cleanupRawAfterTransition_whenDeleteFails_keepsRecordForRetry() {
        rawCleanupProperties.setDeleteObjectEnabled(true);
        MediaFileRecord candidate = createFileRecord(104L, MediaFileStatus.READY, "raw/104.jpg");
        when(mediaFileRecordRepository.findRawDeletionCandidates(any(), any(Pageable.class)))
                .thenReturn(List.of(candidate));
        when(mediaDerivativeRepository.findTopByMediaIdAndDerivativeProfileAndStatusOrderByMediaVersionDescCreatedAtDesc(
                104L,
                MediaDerivativeProfile.THUMBNAIL_WEBP,
                MediaDerivativeStatus.READY
        )).thenReturn(Optional.of(createDerivative(104L, MediaDerivativeProfile.THUMBNAIL_WEBP, LocalDateTime.now().minusDays(12))));
        when(mediaDerivativeRepository.findTopByMediaIdAndDerivativeProfileAndStatusOrderByMediaVersionDescCreatedAtDesc(
                104L,
                MediaDerivativeProfile.DISPLAY_WEBP,
                MediaDerivativeStatus.READY
        )).thenReturn(Optional.of(createDerivative(104L, MediaDerivativeProfile.DISPLAY_WEBP, LocalDateTime.now().minusDays(11))));
        doThrow(new RuntimeException("delete failed"))
                .when(mediaWorkerS3Client)
                .deleteObject(any(DeleteObjectRequest.class));

        MediaRawCleanupService.CleanupResult result = mediaRawCleanupService.cleanupRawAfterTransition();

        assertThat(result.scannedCount()).isEqualTo(1);
        assertThat(result.purgedCount()).isEqualTo(0);
        assertThat(result.deletedObjectCount()).isEqualTo(0);
        assertThat(result.deleteFailedCount()).isEqualTo(1);
        assertThat(candidate.getStatus()).isEqualTo(MediaFileStatus.READY);
        assertThat(candidate.isDeleted()).isFalse();
    }

    private MediaDerivative createDerivative(Long mediaId,
                                             MediaDerivativeProfile profile,
                                             LocalDateTime readyAt) {
        MediaDerivative derivative = MediaDerivative.createReady(
                mediaId,
                profile,
                1L,
                "derived/" + mediaId + ".webp",
                "https://cdn.example.com/derived/" + mediaId + ".webp",
                640,
                360,
                "image/webp",
                1024L
        );
        ReflectionTestUtils.setField(derivative, "createdAt", readyAt.minusMinutes(1));
        ReflectionTestUtils.setField(derivative, "updatedAt", readyAt);
        return derivative;
    }

    private MediaFileRecord createFileRecord(Long mediaId, MediaFileStatus status, String objectKey) {
        try {
            Constructor<MediaFileRecord> constructor = MediaFileRecord.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            MediaFileRecord record = constructor.newInstance();
            ReflectionTestUtils.setField(record, "id", mediaId);
            ReflectionTestUtils.setField(record, "status", status);
            ReflectionTestUtils.setField(record, "bucketName", "team2-donmoa-media-raw");
            ReflectionTestUtils.setField(record, "objectKey", objectKey);
            return record;
        } catch (Exception e) {
            throw new IllegalStateException("failed to create media file record test fixture", e);
        }
    }
}
