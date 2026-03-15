package com.example.media.service.query;

import com.example.media.config.MediaS3Properties;
import com.example.media.config.MediaUrlAccessType;
import com.example.media.config.MediaUrlProperties;
import com.example.media.dto.query.response.MediaUrlResponse;
import com.example.media.entity.MediaDerivative;
import com.example.media.entity.MediaDerivativeProfile;
import com.example.media.entity.MediaFile;
import com.example.media.entity.MediaLink;
import com.example.media.entity.MediaOwnerType;
import com.example.media.entity.MediaUsageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MediaUrlAssemblerTest {

    private MediaUrlAssembler mediaUrlAssembler;

    @BeforeEach
    void setUp() {
        MediaS3Properties mediaS3Properties = new MediaS3Properties();
        mediaS3Properties.setCloudfrontDomain("https://d111111abcdef8.cloudfront.net");

        MediaUrlProperties mediaUrlProperties = new MediaUrlProperties();
        mediaUrlProperties.setAccessType(MediaUrlAccessType.PUBLIC);

        mediaUrlAssembler = new MediaUrlAssembler(new MediaUrlPolicyService(mediaS3Properties, mediaUrlProperties));
    }

    @Test
    void assemble_prefersThumbnailDerivativeForThumbnailUsage() {
        MediaFile mediaFile = createConfirmedMediaFile(101L, "raw/original.jpg");
        MediaLink latestLink = MediaLink.create(101L, MediaOwnerType.ITEM, 200L, MediaUsageType.THUMBNAIL, 0);
        MediaDerivative thumbnail = MediaDerivative.createReady(
                101L,
                MediaDerivativeProfile.THUMBNAIL_WEBP,
                2L,
                "derived/thumbnail.webp",
                "https://cdn.example.com/derived/thumbnail.webp",
                640,
                360,
                "image/webp",
                12345L
        );

        MediaUrlResponse result = mediaUrlAssembler.assemble(
                mediaFile,
                new LatestMediaVariantView(101L, latestLink, Map.of(MediaDerivativeProfile.THUMBNAIL_WEBP, thumbnail))
        );

        assertThat(result.getObjectKey()).isEqualTo("derived/thumbnail.webp");
        assertThat(result.getUsageType()).isEqualTo("THUMBNAIL");
        assertThat(result.getStatus()).isEqualTo("CONFIRMED");
        assertThat(result.getCacheControl()).isNotBlank();
    }

    @Test
    void assemble_fallsBackToDisplayDerivativeForNonThumbnailUsage() {
        MediaFile mediaFile = createConfirmedMediaFile(102L, "raw/original.jpg");
        MediaLink latestLink = MediaLink.create(102L, MediaOwnerType.ITEM, 200L, MediaUsageType.GALLERY, 0);
        MediaDerivative display = MediaDerivative.createReady(
                102L,
                MediaDerivativeProfile.DISPLAY_WEBP,
                1L,
                "derived/display.webp",
                "https://cdn.example.com/derived/display.webp",
                1280,
                720,
                "image/webp",
                22345L
        );

        MediaUrlResponse result = mediaUrlAssembler.assemble(
                mediaFile,
                new LatestMediaVariantView(102L, latestLink, Map.of(MediaDerivativeProfile.DISPLAY_WEBP, display))
        );

        assertThat(result.getObjectKey()).isEqualTo("derived/display.webp");
        assertThat(result.getUsageType()).isEqualTo("GALLERY");
    }

    @Test
    void assemble_fallsBackToOriginalObjectKeyWhenNoDerivativeExists() {
        MediaFile mediaFile = createConfirmedMediaFile(103L, "raw/original.jpg");

        MediaUrlResponse result = mediaUrlAssembler.assemble(
                mediaFile,
                LatestMediaVariantView.empty(103L)
        );

        assertThat(result.getObjectKey()).isEqualTo("raw/original.jpg");
        assertThat(result.getUsageType()).isNull();
        assertThat(result.getMediaUrl()).contains("cloudfront.net/raw/original.jpg");
    }

    private MediaFile createConfirmedMediaFile(Long mediaId, String objectKey) {
        MediaFile mediaFile = MediaFile.createPending(
                100L,
                "sample.jpg",
                objectKey,
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
        ReflectionTestUtils.setField(mediaFile, "id", mediaId);
        mediaFile.confirm(1024L, "image/jpeg", "etag", LocalDateTime.now());
        return mediaFile;
    }
}
