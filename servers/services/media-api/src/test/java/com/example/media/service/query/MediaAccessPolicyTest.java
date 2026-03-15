package com.example.media.service.query;

import com.example.core.exception.BusinessException;
import com.example.media.entity.MediaFile;
import com.example.media.exception.MediaErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaAccessPolicyTest {

    private final MediaAccessPolicy mediaAccessPolicy = new MediaAccessPolicy();

    @Test
    void validateReadAccess_allowsInternalContext() {
        MediaFile mediaFile = createConfirmedMediaFile(100L);

        assertThatCode(() -> mediaAccessPolicy.validateReadAccess(mediaFile, MediaAccessContext.internal()))
                .doesNotThrowAnyException();
    }

    @Test
    void validateReadAccess_blocksAuthenticatedNonOwner() {
        MediaFile mediaFile = createConfirmedMediaFile(100L);

        assertThatThrownBy(() -> mediaAccessPolicy.validateReadAccess(mediaFile, MediaAccessContext.authenticated(999L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MediaErrorCode.FORBIDDEN_MEDIA_ACCESS);
    }

    private MediaFile createConfirmedMediaFile(Long uploaderId) {
        MediaFile mediaFile = MediaFile.createPending(
                uploaderId,
                "sample.jpg",
                "team2-donmoa-media/raw/2026/01/01/sample.jpg",
                "team2-donmoa-media-raw",
                "image/jpeg",
                1024L,
                null,
                null,
                null,
                null,
                "upload-token",
                LocalDateTime.now().plusMinutes(1)
        );
        mediaFile.confirm(1024L, "image/jpeg", "etag", LocalDateTime.now());
        return mediaFile;
    }
}
