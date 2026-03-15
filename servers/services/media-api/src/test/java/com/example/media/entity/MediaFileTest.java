package com.example.media.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MediaFileTest {

    @Test
    void createPending_initializesPendingStateAndRequestedBinding() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 3, 14, 10, 0);

        MediaFile mediaFile = MediaFile.createPending(
                101L,
                "poster.png",
                "raw/poster.png",
                "media-bucket",
                "image/png",
                2048L,
                MediaOwnerType.STORE,
                301L,
                MediaUsageType.THUMBNAIL,
                1,
                "upload-token",
                expiresAt
        );

        assertThat(mediaFile.getStatus()).isEqualTo(MediaStatus.PENDING_UPLOAD);
        assertThat(mediaFile.getUploadToken()).isEqualTo("upload-token");
        assertThat(mediaFile.getUploadTokenExpiresAt()).isEqualTo(expiresAt);
        assertThat(mediaFile.hasRequestedBinding()).isTrue();
    }

    @Test
    void isOwnedBy_returnsTrueOnlyForExactUploader() {
        MediaFile mediaFile = createPendingMediaFile(101L);

        assertThat(mediaFile.isOwnedBy(101L)).isTrue();
        assertThat(mediaFile.isOwnedBy(202L)).isFalse();
        assertThat(mediaFile.isOwnedBy(null)).isFalse();
    }

    @Test
    void isUploadTokenExpired_usesStrictBeforeComparison() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 3, 14, 10, 0);
        MediaFile mediaFile = createPendingMediaFile(101L, expiresAt);

        assertThat(mediaFile.isUploadTokenExpired(expiresAt.minusSeconds(1))).isFalse();
        assertThat(mediaFile.isUploadTokenExpired(expiresAt)).isFalse();
        assertThat(mediaFile.isUploadTokenExpired(expiresAt.plusSeconds(1))).isTrue();
    }

    @Test
    void confirm_setsConfirmedSnapshotAndClearsFailureReason() {
        MediaFile mediaFile = createPendingMediaFile(101L);
        LocalDateTime confirmedAt = LocalDateTime.of(2026, 3, 14, 9, 30);
        mediaFile.markFailed("etag mismatch");

        mediaFile.confirm(4096L, "image/jpeg", "etag-123", confirmedAt);

        assertThat(mediaFile.getStatus()).isEqualTo(MediaStatus.CONFIRMED);
        assertThat(mediaFile.isAlreadyConfirmed()).isTrue();
        assertThat(mediaFile.getFileSize()).isEqualTo(4096L);
        assertThat(mediaFile.getContentType()).isEqualTo("image/jpeg");
        assertThat(mediaFile.getEtag()).isEqualTo("etag-123");
        assertThat(mediaFile.getConfirmedAt()).isEqualTo(confirmedAt);
        assertThat(mediaFile.getTokenUsedAt()).isEqualTo(confirmedAt);
        assertThat(mediaFile.getLastErrorReason()).isNull();
    }

    @Test
    void markReady_promotesConfirmedMediaToReadyWithoutLosingConfirmedState() {
        MediaFile mediaFile = createPendingMediaFile(101L);
        LocalDateTime confirmedAt = LocalDateTime.of(2026, 3, 14, 9, 30);
        mediaFile.confirm(4096L, "image/jpeg", "etag-123", confirmedAt);

        mediaFile.markReady();

        assertThat(mediaFile.getStatus()).isEqualTo(MediaStatus.READY);
        assertThat(mediaFile.isAlreadyConfirmed()).isTrue();
        assertThat(mediaFile.getConfirmedAt()).isEqualTo(confirmedAt);
        assertThat(mediaFile.getTokenUsedAt()).isEqualTo(confirmedAt);
    }

    @Test
    void markDeleted_setsDeletedStatusAndSoftDeleteFlag() {
        MediaFile mediaFile = createPendingMediaFile(101L);

        mediaFile.markDeleted();

        assertThat(mediaFile.getStatus()).isEqualTo(MediaStatus.DELETED);
        assertThat(mediaFile.isDeleted()).isTrue();
        assertThat(mediaFile.getDeletedAt()).isNotNull();
    }

    private MediaFile createPendingMediaFile(Long uploaderId) {
        return createPendingMediaFile(uploaderId, LocalDateTime.of(2026, 3, 14, 10, 0));
    }

    private MediaFile createPendingMediaFile(Long uploaderId, LocalDateTime expiresAt) {
        return MediaFile.createPending(
                uploaderId,
                "poster.png",
                "raw/poster.png",
                "media-bucket",
                "image/png",
                2048L,
                MediaOwnerType.STORE,
                301L,
                MediaUsageType.THUMBNAIL,
                1,
                "upload-token",
                expiresAt
        );
    }
}
