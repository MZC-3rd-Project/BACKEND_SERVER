package com.example.media.service.query;

import com.example.core.exception.BusinessException;
import com.example.media.config.MediaS3Properties;
import com.example.media.config.MediaUrlAccessType;
import com.example.media.config.MediaUrlProperties;
import com.example.media.entity.MediaDerivative;
import com.example.media.entity.MediaDerivativeProfile;
import com.example.media.entity.MediaDerivativeStatus;
import com.example.media.dto.query.response.MediaUrlResponse;
import com.example.media.entity.MediaFile;
import com.example.media.entity.MediaLink;
import com.example.media.entity.MediaOwnerType;
import com.example.media.entity.MediaUsageType;
import com.example.media.exception.MediaErrorCode;
import com.example.media.repository.MediaDerivativeRepository;
import com.example.media.repository.MediaFileRepository;
import com.example.media.repository.MediaLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
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

    @Mock
    private MediaDerivativeRepository mediaDerivativeRepository;

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
        mediaQueryService = new MediaQueryService(
                mediaFileRepository,
                mediaLinkRepository,
                mediaDerivativeRepository,
                mediaUrlPolicyService
        );
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
        when(mediaDerivativeRepository.findByMediaIdInAndDerivativeProfileAndStatusOrderByMediaIdAscMediaVersionDescCreatedAtDesc(
                List.of(101L),
                MediaDerivativeProfile.THUMBNAIL_WEBP,
                MediaDerivativeStatus.READY
        )).thenReturn(List.of());

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
        when(mediaDerivativeRepository.findByMediaIdInAndDerivativeProfileAndStatusOrderByMediaIdAscMediaVersionDescCreatedAtDesc(
                List.of(102L),
                MediaDerivativeProfile.THUMBNAIL_WEBP,
                MediaDerivativeStatus.READY
        )).thenReturn(List.of());

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

    @Test
    void getMediaUrls_returnsOnlyAccessibleReadyMedia() {
        MediaFile confirmed = MediaFile.createPending(
                100L,
                "ready.jpg",
                "team2-donmoa-media/raw/2026/01/01/ready.jpg",
                "team2-donmoa-media-raw",
                "image/jpeg",
                1024L,
                null,
                null,
                null,
                null,
                "upload-token-ready",
                LocalDateTime.now().plusMinutes(1)
        );
        ReflectionTestUtils.setField(confirmed, "id", 201L);
        confirmed.confirm(1024L, "image/jpeg", "etag-ready", LocalDateTime.now());

        MediaFile pending = MediaFile.createPending(
                100L,
                "pending.jpg",
                "team2-donmoa-media/raw/2026/01/01/pending.jpg",
                "team2-donmoa-media-raw",
                "image/jpeg",
                512L,
                null,
                null,
                null,
                null,
                "upload-token-pending",
                LocalDateTime.now().plusMinutes(1)
        );
        ReflectionTestUtils.setField(pending, "id", 202L);

        MediaLink link = MediaLink.create(201L, MediaOwnerType.ITEM, 333L, MediaUsageType.THUMBNAIL, 0);

        when(mediaFileRepository.findAllById(List.of(201L, 202L, 999L)))
                .thenReturn(List.of(confirmed, pending));
        when(mediaLinkRepository.findByMediaIdInOrderByMediaIdAscCreatedAtDesc(List.of(201L, 202L, 999L)))
                .thenReturn(List.of(link));
        when(mediaDerivativeRepository.findByMediaIdInAndDerivativeProfileAndStatusOrderByMediaIdAscMediaVersionDescCreatedAtDesc(
                List.of(201L, 202L, 999L),
                MediaDerivativeProfile.THUMBNAIL_WEBP,
                MediaDerivativeStatus.READY
        )).thenReturn(List.of());

        List<MediaUrlResponse> responses = mediaQueryService.getMediaUrls(List.of(201L, 202L, 999L), 100L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getMediaId()).isEqualTo(201L);
        assertThat(responses.get(0).getMediaUrl()).contains("cloudfront.net");
    }

    @Test
    void getMediaUrls_prefersReadyThumbnailDerivativeForThumbnailUsage() {
        MediaFile confirmed = MediaFile.createPending(
                100L,
                "ready-derived.jpg",
                "team2-donmoa-media/raw/2026/01/01/ready-derived.jpg",
                "team2-donmoa-media-raw",
                "image/jpeg",
                1024L,
                null,
                null,
                null,
                null,
                "upload-token-derived",
                LocalDateTime.now().plusMinutes(1)
        );
        ReflectionTestUtils.setField(confirmed, "id", 301L);
        confirmed.confirm(1024L, "image/jpeg", "etag-derived", LocalDateTime.now());

        MediaLink link = MediaLink.create(301L, MediaOwnerType.ITEM, 777L, MediaUsageType.THUMBNAIL, 0);
        MediaDerivative derivative = createReadyDerivative(
                301L,
                "team2-donmoa-media/derived/2026/01/01/ready-derived_thumbnail_webp_v1.webp"
        );

        when(mediaFileRepository.findAllById(List.of(301L)))
                .thenReturn(List.of(confirmed));
        when(mediaLinkRepository.findByMediaIdInOrderByMediaIdAscCreatedAtDesc(List.of(301L)))
                .thenReturn(List.of(link));
        when(mediaDerivativeRepository.findByMediaIdInAndDerivativeProfileAndStatusOrderByMediaIdAscMediaVersionDescCreatedAtDesc(
                List.of(301L),
                MediaDerivativeProfile.THUMBNAIL_WEBP,
                MediaDerivativeStatus.READY
        )).thenReturn(List.of(derivative));

        List<MediaUrlResponse> responses = mediaQueryService.getMediaUrls(List.of(301L), 100L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getObjectKey()).isEqualTo(derivative.getObjectKey());
        assertThat(responses.get(0).getMediaUrl()).contains("/derived/");
    }

    private MediaDerivative createReadyDerivative(Long mediaId, String objectKey) {
        MediaDerivative derivative = MediaDerivative.createReady(
                mediaId,
                MediaDerivativeProfile.THUMBNAIL_WEBP,
                1L,
                objectKey,
                "https://cdn.example.com/" + objectKey,
                640,
                360,
                "image/webp",
                12345L
        );
        ReflectionTestUtils.setField(derivative, "id", 9001L);
        return derivative;
    }
}
