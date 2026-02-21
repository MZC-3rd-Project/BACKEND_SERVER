package com.example.media.service.command;

import com.example.core.exception.BusinessException;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.media.config.MediaCleanupProperties;
import com.example.media.config.MediaS3Properties;
import com.example.media.dto.command.request.UploadConfirmRequest;
import com.example.media.dto.command.request.UploadIntentRequest;
import com.example.media.dto.command.response.UploadConfirmResponse;
import com.example.media.dto.command.response.UploadIntentResponse;
import com.example.media.entity.MediaFile;
import com.example.media.entity.MediaLink;
import com.example.media.entity.MediaOwnerType;
import com.example.media.entity.MediaStatus;
import com.example.media.entity.MediaUsageType;
import com.example.media.event.MediaConfirmedEvent;
import com.example.media.exception.MediaErrorCode;
import com.example.media.repository.MediaFileRepository;
import com.example.media.repository.MediaLinkRepository;
import com.example.media.service.query.MediaUrlPolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaCommandService {

    private final MediaFileRepository mediaFileRepository;
    private final MediaLinkRepository mediaLinkRepository;
    private final MediaS3Properties mediaS3Properties;
    private final MediaCleanupProperties mediaCleanupProperties;
    private final MediaUrlPolicyService mediaUrlPolicyService;
    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final EventPublisher eventPublisher;

    @Transactional
    public UploadIntentResponse createUploadIntent(UploadIntentRequest request, Long userId) {
        if (request.getFileSize() > mediaS3Properties.getMaxFileSizeBytes()) {
            throw new BusinessException(MediaErrorCode.FILE_SIZE_EXCEEDED);
        }

        MediaBinding requestedBinding = resolveBinding(
                request.getOwnerType(),
                request.getOwnerId(),
                request.getUsageType(),
                request.getSortOrder()
        );

        String objectKey = buildObjectKey(request.getFileName());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusSeconds(mediaS3Properties.getPresignedPutTtlSeconds());
        String uploadToken = UUID.randomUUID().toString();

        MediaFile mediaFile = MediaFile.createPending(
                userId,
                request.getFileName(),
                objectKey,
                mediaS3Properties.getBucket(),
                request.getContentType(),
                request.getFileSize(),
                requestedBinding != null ? requestedBinding.ownerType() : null,
                requestedBinding != null ? requestedBinding.ownerId() : null,
                requestedBinding != null ? requestedBinding.usageType() : null,
                requestedBinding != null ? requestedBinding.sortOrder() : null,
                uploadToken,
                expiresAt
        );
        MediaFile saved = mediaFileRepository.save(mediaFile);

        String presignedUrl = createPresignedPutUrl(objectKey, request.getContentType(), request.getFileSize());

        return UploadIntentResponse.builder()
                .mediaId(saved.getId())
                .objectKey(objectKey)
                .presignedUrl(presignedUrl)
                .uploadToken(uploadToken)
                .expiresAt(expiresAt.toInstant(ZoneOffset.UTC))
                .ownerType(requestedBinding != null ? requestedBinding.ownerType().name() : null)
                .ownerId(requestedBinding != null ? requestedBinding.ownerId() : null)
                .usageType(requestedBinding != null ? requestedBinding.usageType().name() : null)
                .sortOrder(requestedBinding != null ? requestedBinding.sortOrder() : null)
                .build();
    }

    @Transactional
    public UploadConfirmResponse confirmUpload(UploadConfirmRequest request, Long userId) {
        MediaFile mediaFile = mediaFileRepository.findById(request.getMediaId())
                .orElseThrow(() -> new BusinessException(MediaErrorCode.MEDIA_NOT_FOUND));

        validateUploaderAccess(userId, mediaFile);

        MediaBinding requestBinding = resolveBinding(
                request.getOwnerType(),
                request.getOwnerId(),
                request.getUsageType(),
                request.getSortOrder()
        );
        MediaBinding effectiveBinding = requestBinding != null ? requestBinding : resolveRequestedBinding(mediaFile);

        if (mediaFile.isAlreadyConfirmed()) {
            MediaLink existingOrNewLink = upsertMediaLink(mediaFile, effectiveBinding);
            log.info("[MediaConfirm] idempotent confirm hit. mediaId={}, status={}", mediaFile.getId(), mediaFile.getStatus());
            return toConfirmResponse(mediaFile, existingOrNewLink);
        }

        LocalDateTime now = LocalDateTime.now();
        if (!mediaFile.hasSameUploadToken(request.getUploadToken())) {
            throw new BusinessException(MediaErrorCode.INVALID_UPLOAD_TOKEN);
        }
        if (mediaFile.isUploadTokenExpired(now)) {
            mediaFile.markExpired();
            throw new BusinessException(MediaErrorCode.EXPIRED_UPLOAD_TOKEN);
        }

        HeadObjectResponse headObjectResponse = readS3HeadObject(mediaFile);
        validateS3Metadata(mediaFile, headObjectResponse);
        mediaFile.confirm(
                headObjectResponse.contentLength(),
                normalizeContentType(headObjectResponse.contentType(), mediaFile.getRequestedContentType()),
                normalizeEtag(headObjectResponse.eTag()),
                now
        );

        MediaFile saved = mediaFileRepository.save(mediaFile);
        MediaLink mediaLink = upsertMediaLink(saved, effectiveBinding);
        publishConfirmedEvent(saved, mediaLink);
        log.info(
                "[MediaConfirm] confirm success. mediaId={}, objectKey={}, fileSize={}, ownerType={}, ownerId={}, usageType={}",
                saved.getId(),
                saved.getObjectKey(),
                saved.getFileSize(),
                mediaLink != null ? mediaLink.getOwnerType() : null,
                mediaLink != null ? mediaLink.getOwnerId() : null,
                mediaLink != null ? mediaLink.getUsageType() : null
        );
        return toConfirmResponse(saved, mediaLink);
    }

    @Transactional
    public ExpireResult expirePendingUploads() {
        LocalDateTime now = LocalDateTime.now();
        int batchSize = Math.max(1, mediaCleanupProperties.getBatchSize());

        List<MediaFile> targets = mediaFileRepository.findByStatusAndUploadTokenExpiresAtBefore(
                MediaStatus.PENDING_UPLOAD,
                now,
                PageRequest.of(0, batchSize)
        );
        int deletedObjectCount = 0;
        int deleteFailedCount = 0;

        for (MediaFile target : targets) {
            target.markExpired();
            if (mediaCleanupProperties.isDeleteObjectEnabled()) {
                if (deleteS3ObjectQuietly(target)) {
                    deletedObjectCount++;
                } else {
                    deleteFailedCount++;
                }
            }
        }
        return new ExpireResult(targets.size(), deletedObjectCount, deleteFailedCount);
    }

    private void validateUploaderAccess(Long userId, MediaFile mediaFile) {
        if (userId != null && mediaFile.getUploaderId() != null && !mediaFile.isOwnedBy(userId)) {
            throw new BusinessException(MediaErrorCode.FORBIDDEN_MEDIA_ACCESS);
        }
    }

    private MediaBinding resolveRequestedBinding(MediaFile mediaFile) {
        if (!mediaFile.hasRequestedBinding()) {
            return null;
        }
        return new MediaBinding(
                mediaFile.getRequestedOwnerType(),
                mediaFile.getRequestedOwnerId(),
                mediaFile.getRequestedUsageType() != null ? mediaFile.getRequestedUsageType() : MediaUsageType.ATTACHMENT,
                mediaFile.getRequestedSortOrder()
        );
    }

    private MediaBinding resolveBinding(String ownerTypeValue, Long ownerId, String usageTypeValue, Integer sortOrder) {
        boolean hasOwnerType = StringUtils.hasText(ownerTypeValue);
        boolean hasOwnerId = ownerId != null;
        boolean hasUsageType = StringUtils.hasText(usageTypeValue);
        boolean hasSortOrder = sortOrder != null;

        if (!hasOwnerType && !hasOwnerId && !hasUsageType && !hasSortOrder) {
            return null;
        }
        if (!hasOwnerType || !hasOwnerId) {
            throw new BusinessException(MediaErrorCode.INVALID_MEDIA_BINDING);
        }
        if (sortOrder != null && sortOrder < 0) {
            throw new BusinessException(MediaErrorCode.INVALID_MEDIA_BINDING);
        }

        try {
            MediaOwnerType ownerType = MediaOwnerType.fromNullable(ownerTypeValue);
            MediaUsageType usageType = MediaUsageType.fromNullable(usageTypeValue);
            if (ownerType == null) {
                throw new BusinessException(MediaErrorCode.INVALID_MEDIA_BINDING);
            }
            return new MediaBinding(
                    ownerType,
                    ownerId,
                    usageType != null ? usageType : MediaUsageType.ATTACHMENT,
                    sortOrder
            );
        } catch (IllegalArgumentException e) {
            throw new BusinessException(MediaErrorCode.INVALID_MEDIA_BINDING, e);
        }
    }

    private MediaLink upsertMediaLink(MediaFile mediaFile, MediaBinding binding) {
        if (binding == null) {
            return null;
        }
        List<MediaLink> links = mediaLinkRepository.findByOwnerTypeAndOwnerIdAndUsageTypeOrderBySortOrderAscCreatedAtAsc(
                binding.ownerType(),
                binding.ownerId(),
                binding.usageType()
        );

        MediaLink target = links.stream()
                .filter(link -> link.matchesMedia(mediaFile.getId()))
                .findFirst()
                .orElse(null);

        if (target == null) {
            target = MediaLink.create(
                    mediaFile.getId(),
                    binding.ownerType(),
                    binding.ownerId(),
                    binding.usageType(),
                    links.size()
            );
            links.add(target);
        }

        if (binding.sortOrder() != null) {
            links.remove(target);
            int targetIndex = Math.max(0, Math.min(binding.sortOrder(), links.size()));
            links.add(targetIndex, target);
        }

        for (int i = 0; i < links.size(); i++) {
            links.get(i).updateSortOrder(i);
        }
        mediaLinkRepository.saveAll(links);
        return target;
    }

    private void publishConfirmedEvent(MediaFile mediaFile, MediaLink mediaLink) {
        eventPublisher.publish(
                new MediaConfirmedEvent(
                        mediaFile.getId(),
                        mediaFile.getUploaderId(),
                        mediaFile.getBucketName(),
                        mediaFile.getObjectKey(),
                        mediaFile.getContentType(),
                        mediaFile.getFileSize(),
                        mediaFile.getEtag(),
                        mediaLink != null ? mediaLink.getOwnerType() : null,
                        mediaLink != null ? mediaLink.getOwnerId() : null,
                        mediaLink != null ? mediaLink.getUsageType() : null,
                        mediaLink != null ? mediaLink.getSortOrder() : null,
                        mediaUrlPolicyService.buildBaseUrl(mediaFile.getObjectKey())
                ),
                EventMetadata.of("MediaFile", String.valueOf(mediaFile.getId()))
        );
    }

    private HeadObjectResponse readS3HeadObject(MediaFile mediaFile) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(mediaFile.getBucketName())
                    .key(mediaFile.getObjectKey())
                    .build();
            return s3Client.headObject(headObjectRequest);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                mediaFile.markFailed("S3 object not found");
                throw new BusinessException(MediaErrorCode.MEDIA_S3_OBJECT_NOT_FOUND);
            }
            mediaFile.markFailed("S3 headObject failed: " + e.getMessage());
            throw new BusinessException(MediaErrorCode.MEDIA_S3_HEAD_FAILED, e);
        } catch (SdkException e) {
            mediaFile.markFailed("S3 client error: " + e.getMessage());
            throw new BusinessException(MediaErrorCode.MEDIA_S3_HEAD_FAILED, e);
        }
    }

    private void validateS3Metadata(MediaFile mediaFile, HeadObjectResponse headObjectResponse) {
        Long expectedSize = mediaFile.getRequestedFileSize();
        Long actualSize = headObjectResponse.contentLength();
        if (expectedSize != null && actualSize != null && !expectedSize.equals(actualSize)) {
            mediaFile.markFailed("S3 size mismatch. expected=" + expectedSize + ", actual=" + actualSize);
            log.warn(
                    "[MediaConfirm] metadata mismatch(size). mediaId={}, objectKey={}, expectedSize={}, actualSize={}",
                    mediaFile.getId(),
                    mediaFile.getObjectKey(),
                    expectedSize,
                    actualSize
            );
            throw new BusinessException(MediaErrorCode.MEDIA_S3_METADATA_MISMATCH);
        }

        String expectedContentType = normalizeMediaTypeForCompare(mediaFile.getRequestedContentType());
        String actualContentType = normalizeMediaTypeForCompare(headObjectResponse.contentType());
        if (expectedContentType != null && actualContentType != null && !expectedContentType.equals(actualContentType)) {
            mediaFile.markFailed("S3 contentType mismatch. expected=" + expectedContentType + ", actual=" + actualContentType);
            log.warn(
                    "[MediaConfirm] metadata mismatch(contentType). mediaId={}, objectKey={}, expectedType={}, actualType={}",
                    mediaFile.getId(),
                    mediaFile.getObjectKey(),
                    expectedContentType,
                    actualContentType
            );
            throw new BusinessException(MediaErrorCode.MEDIA_S3_METADATA_MISMATCH);
        }
    }

    private boolean deleteS3ObjectQuietly(MediaFile mediaFile) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(mediaFile.getBucketName())
                    .key(mediaFile.getObjectKey())
                    .build();
            s3Client.deleteObject(request);
            return true;
        } catch (Exception e) {
            log.warn(
                    "[MediaCleanup] S3 object delete failed. mediaId={}, objectKey={}, reason={}",
                    mediaFile.getId(),
                    mediaFile.getObjectKey(),
                    e.getMessage()
            );
            return false;
        }
    }

    private String createPresignedPutUrl(String objectKey, String contentType, long contentLength) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(mediaS3Properties.getBucket())
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength(contentLength)
                    .build();

            PutObjectPresignRequest putObjectPresignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(mediaS3Properties.getPresignedPutTtlSeconds()))
                    .putObjectRequest(putObjectRequest)
                    .build();

            PresignedPutObjectRequest presignedPutObjectRequest = s3Presigner.presignPutObject(putObjectPresignRequest);
            URL url = presignedPutObjectRequest.url();
            return url.toString();
        } catch (Exception e) {
            throw new BusinessException(MediaErrorCode.MEDIA_S3_PRESIGN_FAILED, e);
        }
    }

    private String buildObjectKey(String originalFileName) {
        String safeFileName = sanitizeFileName(originalFileName);
        LocalDate today = LocalDate.now();
        String randomId = UUID.randomUUID().toString();
        String prefix = trimSlash(mediaS3Properties.getKeyPrefix());

        return String.format(
                Locale.ROOT,
                "%s/raw/%04d/%02d/%02d/%s_%s",
                prefix,
                today.getYear(),
                today.getMonthValue(),
                today.getDayOfMonth(),
                randomId,
                safeFileName
        );
    }

    private String sanitizeFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new BusinessException(MediaErrorCode.INVALID_MEDIA_REQUEST);
        }

        String normalized = fileName.trim().replaceAll("\\s+", "-");
        normalized = normalized.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (normalized.length() > 120) {
            return normalized.substring(normalized.length() - 120);
        }
        return normalized;
    }

    private String trimSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }

    private String normalizeContentType(String actual, String fallback) {
        return StringUtils.hasText(actual) ? actual : fallback;
    }

    private String normalizeMediaTypeForCompare(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int semicolonIndex = normalized.indexOf(';');
        if (semicolonIndex >= 0) {
            normalized = normalized.substring(0, semicolonIndex).trim();
        }
        return normalized;
    }

    private String normalizeEtag(String etag) {
        if (!StringUtils.hasText(etag)) {
            return null;
        }
        return etag.replace("\"", "");
    }

    private UploadConfirmResponse toConfirmResponse(MediaFile mediaFile, MediaLink mediaLink) {
        MediaUrlPolicyService.MediaUrlContract urlContract = mediaUrlPolicyService.resolve(
                mediaFile.getObjectKey(),
                mediaLink != null ? mediaLink.getUsageType() : null
        );
        return UploadConfirmResponse.builder()
                .mediaId(mediaFile.getId())
                .status(mediaFile.getStatus().name())
                .objectKey(mediaFile.getObjectKey())
                .fileSize(mediaFile.getFileSize())
                .contentType(mediaFile.getContentType())
                .etag(mediaFile.getEtag())
                .mediaUrl(urlContract.url())
                .urlAccessType(urlContract.accessType().name())
                .urlExpiresAt(urlContract.expiresAt())
                .cacheControl(urlContract.cacheControl())
                .linkId(mediaLink != null ? mediaLink.getId() : null)
                .ownerType(mediaLink != null ? mediaLink.getOwnerType().name() : null)
                .ownerId(mediaLink != null ? mediaLink.getOwnerId() : null)
                .usageType(mediaLink != null ? mediaLink.getUsageType().name() : null)
                .sortOrder(mediaLink != null ? mediaLink.getSortOrder() : null)
                .build();
    }

    private record MediaBinding(
            MediaOwnerType ownerType,
            Long ownerId,
            MediaUsageType usageType,
            Integer sortOrder
    ) {
    }

    public record ExpireResult(
            int expiredCount,
            int deletedObjectCount,
            int deleteFailedCount
    ) {
    }
}
