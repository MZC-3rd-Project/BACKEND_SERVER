package com.example.mediaworker.service;

import com.example.mediaworker.config.MediaWorkerCleanupProperties;
import com.example.mediaworker.config.MediaWorkerS3Properties;
import com.example.mediaworker.entity.MediaDerivative;
import com.example.mediaworker.entity.MediaDerivativeProfile;
import com.example.mediaworker.entity.MediaDerivativeStatus;
import com.example.mediaworker.repository.MediaDerivativeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaDerivativeCleanupServiceTest {

    @Mock
    private MediaDerivativeRepository mediaDerivativeRepository;

    @Mock
    private S3Client mediaWorkerS3Client;

    private MediaWorkerCleanupProperties mediaWorkerCleanupProperties;
    private MediaWorkerS3Properties mediaWorkerS3Properties;
    private MediaDerivativeCleanupService mediaDerivativeCleanupService;

    @BeforeEach
    void setUp() {
        mediaWorkerCleanupProperties = new MediaWorkerCleanupProperties();
        mediaWorkerCleanupProperties.setEnabled(true);
        mediaWorkerCleanupProperties.setBatchSize(20);
        mediaWorkerCleanupProperties.setDeleteObjectEnabled(false);

        mediaWorkerS3Properties = new MediaWorkerS3Properties();
        mediaWorkerS3Properties.setBucket("team2-donmoa-media-raw");

        mediaDerivativeCleanupService = new MediaDerivativeCleanupService(
                mediaDerivativeRepository,
                mediaWorkerCleanupProperties,
                mediaWorkerS3Properties,
                mediaWorkerS3Client
        );
    }

    @Test
    void cleanupOrphanedDerivatives_whenDeleteEnabledAndSuccess_marksDeleted() {
        mediaWorkerCleanupProperties.setDeleteObjectEnabled(true);
        MediaDerivative derivative = createDerivative(1001L, 41L, "team2-donmoa-media/derived/sample_41.webp");
        when(mediaDerivativeRepository.findOrphanedDerivatives(eq(MediaDerivativeStatus.READY), any(Pageable.class)))
                .thenReturn(List.of(derivative));

        MediaDerivativeCleanupService.CleanupResult result = mediaDerivativeCleanupService.cleanupOrphanedDerivatives();

        assertThat(result.orphanCount()).isEqualTo(1);
        assertThat(result.purgedCount()).isEqualTo(1);
        assertThat(result.deletedObjectCount()).isEqualTo(1);
        assertThat(result.deleteFailedCount()).isEqualTo(0);
        assertThat(derivative.getStatus()).isEqualTo(MediaDerivativeStatus.DELETED);
        assertThat(derivative.isDeleted()).isTrue();
        verify(mediaWorkerS3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void cleanupOrphanedDerivatives_whenDeleteFails_keepsDerivativeForRetry() {
        mediaWorkerCleanupProperties.setDeleteObjectEnabled(true);
        MediaDerivative derivative = createDerivative(1002L, 42L, "team2-donmoa-media/derived/sample_42.webp");
        when(mediaDerivativeRepository.findOrphanedDerivatives(eq(MediaDerivativeStatus.READY), any(Pageable.class)))
                .thenReturn(List.of(derivative));
        doThrow(new RuntimeException("s3 delete failed"))
                .when(mediaWorkerS3Client)
                .deleteObject(any(DeleteObjectRequest.class));

        MediaDerivativeCleanupService.CleanupResult result = mediaDerivativeCleanupService.cleanupOrphanedDerivatives();

        assertThat(result.orphanCount()).isEqualTo(1);
        assertThat(result.purgedCount()).isEqualTo(0);
        assertThat(result.deletedObjectCount()).isEqualTo(0);
        assertThat(result.deleteFailedCount()).isEqualTo(1);
        assertThat(derivative.getStatus()).isEqualTo(MediaDerivativeStatus.READY);
        assertThat(derivative.isDeleted()).isFalse();
    }

    @Test
    void cleanupOrphanedDerivatives_whenDeleteDisabled_softDeletesWithoutS3Call() {
        mediaWorkerCleanupProperties.setDeleteObjectEnabled(false);
        MediaDerivative derivative = createDerivative(1003L, 43L, "team2-donmoa-media/derived/sample_43.webp");
        when(mediaDerivativeRepository.findOrphanedDerivatives(eq(MediaDerivativeStatus.READY), any(Pageable.class)))
                .thenReturn(List.of(derivative));

        MediaDerivativeCleanupService.CleanupResult result = mediaDerivativeCleanupService.cleanupOrphanedDerivatives();

        assertThat(result.orphanCount()).isEqualTo(1);
        assertThat(result.purgedCount()).isEqualTo(1);
        assertThat(result.deletedObjectCount()).isEqualTo(0);
        assertThat(result.deleteFailedCount()).isEqualTo(0);
        assertThat(derivative.getStatus()).isEqualTo(MediaDerivativeStatus.DELETED);
        assertThat(derivative.isDeleted()).isTrue();
        verify(mediaWorkerS3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    private MediaDerivative createDerivative(Long derivativeId, Long mediaId, String objectKey) {
        MediaDerivative derivative = MediaDerivative.createReady(
                mediaId,
                MediaDerivativeProfile.THUMBNAIL_WEBP,
                1L,
                objectKey,
                "https://cdn.example.com/" + objectKey,
                320,
                180,
                "image/webp",
                2_048L
        );
        ReflectionTestUtils.setField(derivative, "id", derivativeId);
        return derivative;
    }
}
