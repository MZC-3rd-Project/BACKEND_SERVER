package com.example.media.service.command;

import com.example.core.exception.BusinessException;
import com.example.event.EventPublisher;
import com.example.media.config.MediaCleanupProperties;
import com.example.media.config.MediaS3Properties;
import com.example.media.config.MediaUrlAccessType;
import com.example.media.config.MediaUrlProperties;
import com.example.media.dto.command.request.UploadConfirmRequest;
import com.example.media.dto.command.response.UploadConfirmResponse;
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
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaCommandServiceTest {

    @Mock
    private MediaFileRepository mediaFileRepository;

    @Mock
    private MediaLinkRepository mediaLinkRepository;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Client s3Client;

    @Mock
    private EventPublisher eventPublisher;

    private MediaS3Properties mediaS3Properties;
    private MediaCleanupProperties mediaCleanupProperties;
    private MediaUrlProperties mediaUrlProperties;

    private MediaCommandService mediaCommandService;

    @BeforeEach
    void setUp() {
        mediaS3Properties = new MediaS3Properties();
        mediaS3Properties.setBucket("team2-donmoa-media-raw");
        mediaS3Properties.setKeyPrefix("team2-donmoa-media");
        mediaS3Properties.setPresignedPutTtlSeconds(300);
        mediaS3Properties.setMaxFileSizeBytes(50_000_000L);
        mediaS3Properties.setCloudfrontDomain("https://d111111abcdef8.cloudfront.net");

        mediaCleanupProperties = new MediaCleanupProperties();
        mediaCleanupProperties.setBatchSize(100);
        mediaCleanupProperties.setDeleteObjectEnabled(false);

        mediaUrlProperties = new MediaUrlProperties();
        mediaUrlProperties.setAccessType(MediaUrlAccessType.PUBLIC);
        mediaUrlProperties.setSignedUrlTtlSeconds(300);

        mediaCommandService = new MediaCommandService(
                mediaFileRepository,
                mediaLinkRepository,
                mediaS3Properties,
                mediaCleanupProperties,
                mediaUrlProperties,
                s3Presigner,
                s3Client,
                eventPublisher
        );
    }

    @Test
    void confirmUpload_withRequestedBinding_createsLinkAndPublishesEvent() {
        MediaFile mediaFile = MediaFile.createPending(
                100L,
                "sample.jpg",
                "team2-donmoa-media/raw/2026/01/01/sample.jpg",
                "team2-donmoa-media-raw",
                "image/jpeg",
                1024L,
                MediaOwnerType.ITEM,
                200L,
                MediaUsageType.GALLERY,
                null,
                "upload-token-1",
                LocalDateTime.now().plusMinutes(5)
        );
        ReflectionTestUtils.setField(mediaFile, "id", 1L);

        UploadConfirmRequest request = new UploadConfirmRequest();
        ReflectionTestUtils.setField(request, "mediaId", 1L);
        ReflectionTestUtils.setField(request, "uploadToken", "upload-token-1");

        when(mediaFileRepository.findById(1L)).thenReturn(Optional.of(mediaFile));
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentLength(1024L)
                .contentType("image/jpeg")
                .eTag("\"etag-1\"")
                .build());
        when(mediaFileRepository.save(any(MediaFile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mediaLinkRepository.findByOwnerTypeAndOwnerIdAndUsageTypeOrderBySortOrderAscCreatedAtAsc(
                MediaOwnerType.ITEM, 200L, MediaUsageType.GALLERY
        )).thenReturn(new ArrayList<>());
        when(mediaLinkRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<MediaLink> links = invocation.getArgument(0);
            long seq = 10L;
            for (MediaLink link : links) {
                if (link.getId() == null) {
                    ReflectionTestUtils.setField(link, "id", seq++);
                }
            }
            return links;
        });

        UploadConfirmResponse response = mediaCommandService.confirmUpload(request, 100L);

        assertThat(response.getStatus()).isEqualTo("CONFIRMED");
        assertThat(response.getOwnerType()).isEqualTo("ITEM");
        assertThat(response.getOwnerId()).isEqualTo(200L);
        assertThat(response.getUsageType()).isEqualTo("GALLERY");
        assertThat(response.getSortOrder()).isEqualTo(0);
        assertThat(response.getLinkId()).isNotNull();
        assertThat(response.getUrlAccessType()).isEqualTo("PUBLIC");
        assertThat(response.getUrlExpiresAt()).isNull();
        assertThat(response.getCacheControl()).isEqualTo(mediaUrlProperties.getDefaultCacheControl());
        verify(eventPublisher).publish(any(), any());
    }

    @Test
    void confirmUpload_whenMetadataMismatch_marksFailedAndThrows() {
        MediaFile mediaFile = MediaFile.createPending(
                100L,
                "sample-mismatch.jpg",
                "team2-donmoa-media/raw/2026/01/01/sample-mismatch.jpg",
                "team2-donmoa-media-raw",
                "image/jpeg",
                1024L,
                MediaOwnerType.ITEM,
                200L,
                MediaUsageType.GALLERY,
                null,
                "upload-token-2",
                LocalDateTime.now().plusMinutes(5)
        );
        ReflectionTestUtils.setField(mediaFile, "id", 2L);

        UploadConfirmRequest request = new UploadConfirmRequest();
        ReflectionTestUtils.setField(request, "mediaId", 2L);
        ReflectionTestUtils.setField(request, "uploadToken", "upload-token-2");

        when(mediaFileRepository.findById(2L)).thenReturn(Optional.of(mediaFile));
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentLength(2048L)
                .contentType("image/jpeg")
                .eTag("\"etag-2\"")
                .build());

        assertThatThrownBy(() -> mediaCommandService.confirmUpload(request, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MediaErrorCode.MEDIA_S3_METADATA_MISMATCH);

        assertThat(mediaFile.getStatus().name()).isEqualTo("FAILED");
        verify(eventPublisher, never()).publish(any(), any());
        verify(mediaFileRepository, never()).save(any(MediaFile.class));
    }

    @Test
    void confirmUpload_whenAlreadyConfirmed_doesNotHeadObjectAgain() {
        MediaFile mediaFile = MediaFile.createPending(
                100L,
                "sample3.jpg",
                "team2-donmoa-media/raw/2026/01/01/sample3.jpg",
                "team2-donmoa-media-raw",
                "image/jpeg",
                2048L,
                MediaOwnerType.ITEM,
                200L,
                MediaUsageType.THUMBNAIL,
                0,
                "upload-token-3",
                LocalDateTime.now().plusMinutes(5)
        );
        ReflectionTestUtils.setField(mediaFile, "id", 31L);
        mediaFile.confirm(2048L, "image/jpeg", "etag-3", LocalDateTime.now());

        UploadConfirmRequest request = new UploadConfirmRequest();
        ReflectionTestUtils.setField(request, "mediaId", 31L);
        ReflectionTestUtils.setField(request, "uploadToken", "upload-token-3");
        ReflectionTestUtils.setField(request, "ownerType", "ITEM");
        ReflectionTestUtils.setField(request, "ownerId", 200L);
        ReflectionTestUtils.setField(request, "usageType", "THUMBNAIL");
        ReflectionTestUtils.setField(request, "sortOrder", 0);

        when(mediaFileRepository.findById(31L)).thenReturn(Optional.of(mediaFile));
        when(mediaLinkRepository.findByOwnerTypeAndOwnerIdAndUsageTypeOrderBySortOrderAscCreatedAtAsc(
                MediaOwnerType.ITEM, 200L, MediaUsageType.THUMBNAIL
        )).thenReturn(new ArrayList<>());
        when(mediaLinkRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        UploadConfirmResponse response = mediaCommandService.confirmUpload(request, 100L);

        assertThat(response.getStatus()).isEqualTo("CONFIRMED");
        assertThat(response.getUrlAccessType()).isEqualTo("PUBLIC");
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
        verify(eventPublisher, never()).publish(any(), any());
        verify(mediaFileRepository, never()).save(any(MediaFile.class));
    }

    @Test
    void confirmUpload_withSignedUrlPolicy_setsExpiration() {
        mediaUrlProperties.setAccessType(MediaUrlAccessType.SIGNED_URL);
        mediaUrlProperties.setSignedUrlTtlSeconds(60);

        MediaFile mediaFile = MediaFile.createPending(
                100L,
                "sample-signed.jpg",
                "team2-donmoa-media/raw/2026/01/01/sample-signed.jpg",
                "team2-donmoa-media-raw",
                "image/jpeg",
                1024L,
                null,
                null,
                null,
                null,
                "upload-token-signed",
                LocalDateTime.now().plusMinutes(5)
        );
        ReflectionTestUtils.setField(mediaFile, "id", 51L);

        UploadConfirmRequest request = new UploadConfirmRequest();
        ReflectionTestUtils.setField(request, "mediaId", 51L);
        ReflectionTestUtils.setField(request, "uploadToken", "upload-token-signed");

        when(mediaFileRepository.findById(51L)).thenReturn(Optional.of(mediaFile));
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentLength(1024L)
                .contentType("image/jpeg")
                .eTag("\"etag-signed\"")
                .build());
        when(mediaFileRepository.save(any(MediaFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UploadConfirmResponse response = mediaCommandService.confirmUpload(request, 100L);

        assertThat(response.getUrlAccessType()).isEqualTo("SIGNED_URL");
        assertThat(response.getUrlExpiresAt()).isNotNull();
    }

    @Test
    void expirePendingUploads_whenDeleteEnabled_marksExpiredAndDeletesObject() {
        mediaCleanupProperties.setBatchSize(2);
        mediaCleanupProperties.setDeleteObjectEnabled(true);

        MediaFile mediaFile = MediaFile.createPending(
                100L,
                "sample-expire.jpg",
                "team2-donmoa-media/raw/2026/01/01/sample-expire.jpg",
                "team2-donmoa-media-raw",
                "image/jpeg",
                512L,
                null,
                null,
                null,
                null,
                "upload-token-expire",
                LocalDateTime.now().minusMinutes(10)
        );
        ReflectionTestUtils.setField(mediaFile, "id", 41L);

        when(mediaFileRepository.findByStatusAndUploadTokenExpiresAtBefore(
                any(), any(), any(Pageable.class)
        )).thenReturn(List.of(mediaFile));

        MediaCommandService.ExpireResult result = mediaCommandService.expirePendingUploads();

        assertThat(result.expiredCount()).isEqualTo(1);
        assertThat(result.deletedObjectCount()).isEqualTo(1);
        assertThat(result.deleteFailedCount()).isEqualTo(0);
        assertThat(mediaFile.getStatus().name()).isEqualTo("EXPIRED");
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }
}
