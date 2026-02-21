package com.example.media.service.query;

import com.example.core.exception.BusinessException;
import com.example.media.config.MediaS3Properties;
import com.example.media.config.MediaUrlAccessType;
import com.example.media.config.MediaUrlProperties;
import com.example.media.dto.query.response.MediaUrlResponse;
import com.example.media.entity.MediaFile;
import com.example.media.entity.MediaLink;
import com.example.media.entity.MediaOwnerType;
import com.example.media.entity.MediaUsageType;
import com.example.media.exception.MediaErrorCode;
import com.example.media.repository.MediaFileRepository;
import com.example.media.repository.MediaLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaQueryServiceTest {

    @Mock
    private MediaFileRepository mediaFileRepository;

    @Mock
    private MediaLinkRepository mediaLinkRepository;

    private MediaQueryService mediaQueryService;

    private MediaUrlProperties mediaUrlProperties;

    @BeforeEach
    void setUp() {
        MediaS3Properties mediaS3Properties = new MediaS3Properties();
        mediaS3Properties.setCloudfrontDomain("https://d111111abcdef8.cloudfront.net");

        mediaUrlProperties = new MediaUrlProperties();
        mediaUrlProperties.setAccessType(MediaUrlAccessType.PUBLIC);
        mediaUrlProperties.setSignedUrlTtlSeconds(300);

        MediaUrlPolicyService mediaUrlPolicyService = new MediaUrlPolicyService(mediaS3Properties, mediaUrlProperties);
        mediaQueryService = new MediaQueryService(mediaFileRepository, mediaLinkRepository, mediaUrlPolicyService);
    }

    @Test
    void getMediaUrl_returnsPublicUrlForConfirmedMedia() {
        MediaFile mediaFile = MediaFile.createPending(
                100L,
                "sample-query.jpg",
                "team2-donmoa-media/raw/2026/01/01/sample-query.jpg",
                "team2-donmoa-media-raw",
                "image/jpeg",
                1024L,
                null,
                null,
                null,
                null,
                "upload-token-query",
                LocalDateTime.now().plusMinutes(1)
        );
        ReflectionTestUtils.setField(mediaFile, "id", 101L);
        mediaFile.confirm(1024L, "image/jpeg", "etag-query", LocalDateTime.now());

        MediaLink mediaLink = MediaLink.create(101L, MediaOwnerType.ITEM, 200L, MediaUsageType.THUMBNAIL, 0);

        when(mediaFileRepository.findById(101L)).thenReturn(Optional.of(mediaFile));
        when(mediaLinkRepository.findTopByMediaIdOrderByCreatedAtDesc(101L)).thenReturn(Optional.of(mediaLink));

        MediaUrlResponse response = mediaQueryService.getMediaUrl(101L, 100L);

        assertThat(response.getMediaId()).isEqualTo(101L);
        assertThat(response.getUrlAccessType()).isEqualTo("PUBLIC");
        assertThat(response.getMediaUrl()).contains("cloudfront.net");
        assertThat(response.getUsageType()).isEqualTo("THUMBNAIL");
        assertThat(response.getUrlExpiresAt()).isNull();
    }

    @Test
    void getMediaUrl_withSignedUrlPolicy_setsExpiresAt() {
        mediaUrlProperties.setAccessType(MediaUrlAccessType.SIGNED_URL);
        mediaUrlProperties.setSignedUrlTtlSeconds(60);

        MediaFile mediaFile = MediaFile.createPending(
                100L,
                "sample-signed-query.jpg",
                "team2-donmoa-media/raw/2026/01/01/sample-signed-query.jpg",
                "team2-donmoa-media-raw",
                "image/jpeg",
                1024L,
                null,
                null,
                null,
                null,
                "upload-token-query2",
                LocalDateTime.now().plusMinutes(1)
        );
        ReflectionTestUtils.setField(mediaFile, "id", 102L);
        mediaFile.confirm(1024L, "image/jpeg", "etag-query2", LocalDateTime.now());

        when(mediaFileRepository.findById(102L)).thenReturn(Optional.of(mediaFile));
        when(mediaLinkRepository.findTopByMediaIdOrderByCreatedAtDesc(102L)).thenReturn(Optional.empty());

        MediaUrlResponse response = mediaQueryService.getMediaUrl(102L, 100L);

        assertThat(response.getUrlAccessType()).isEqualTo("SIGNED_URL");
        assertThat(response.getUrlExpiresAt()).isNotNull();
    }

    @Test
    void getMediaUrl_whenMediaNotReady_throwsConflict() {
        MediaFile mediaFile = MediaFile.createPending(
                100L,
                "sample-pending.jpg",
                "team2-donmoa-media/raw/2026/01/01/sample-pending.jpg",
                "team2-donmoa-media-raw",
                "image/jpeg",
                1024L,
                null,
                null,
                null,
                null,
                "upload-token-query3",
                LocalDateTime.now().plusMinutes(1)
        );
        ReflectionTestUtils.setField(mediaFile, "id", 103L);

        when(mediaFileRepository.findById(103L)).thenReturn(Optional.of(mediaFile));

        assertThatThrownBy(() -> mediaQueryService.getMediaUrl(103L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MediaErrorCode.MEDIA_NOT_READY);
    }
}
