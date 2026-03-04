package com.example.media.service.query;

import com.example.core.exception.BusinessException;
import com.example.media.dto.query.response.MediaUrlResponse;
import com.example.media.entity.MediaDerivative;
import com.example.media.entity.MediaDerivativeProfile;
import com.example.media.entity.MediaDerivativeStatus;
import com.example.media.entity.MediaFile;
import com.example.media.entity.MediaLink;
import com.example.media.entity.MediaStatus;
import com.example.media.entity.MediaUsageType;
import com.example.media.exception.MediaErrorCode;
import com.example.media.repository.MediaDerivativeRepository;
import com.example.media.repository.MediaFileRepository;
import com.example.media.repository.MediaLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MediaQueryService {

    private static final MediaDerivativeProfile THUMBNAIL_PROFILE = MediaDerivativeProfile.THUMBNAIL_WEBP;
    private static final MediaDerivativeProfile DISPLAY_PROFILE = MediaDerivativeProfile.DISPLAY_WEBP;
    private static final List<MediaDerivativeProfile> SERVING_DERIVATIVE_PROFILES =
            List.of(THUMBNAIL_PROFILE, DISPLAY_PROFILE);

    private final MediaFileRepository mediaFileRepository;
    private final MediaLinkRepository mediaLinkRepository;
    private final MediaDerivativeRepository mediaDerivativeRepository;
    private final MediaUrlPolicyService mediaUrlPolicyService;

    @Transactional(readOnly = true)
    public MediaUrlResponse getMediaUrl(Long mediaId, Long userId) {
        MediaFile mediaFile = mediaFileRepository.findById(mediaId)
                .orElseThrow(() -> new BusinessException(MediaErrorCode.MEDIA_NOT_FOUND));

        validateAccess(mediaFile, userId);
        validateStatus(mediaFile);

        MediaLink link = mediaLinkRepository.findTopByMediaIdOrderByCreatedAtDesc(mediaId).orElse(null);
        Map<MediaDerivativeProfile, MediaDerivative> derivativeByProfile =
                findLatestReadyDerivativesByMediaId(List.of(mediaId)).getOrDefault(mediaId, Map.of());
        return toMediaUrlResponse(mediaFile, link, derivativeByProfile);
    }

    @Transactional(readOnly = true)
    public List<MediaUrlResponse> getMediaUrls(List<Long> mediaIds, Long userId) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return List.of();
        }

        List<Long> uniqueIds = mediaIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        if (uniqueIds.isEmpty()) {
            return List.of();
        }

        Map<Long, MediaFile> mediaFileMap = mediaFileRepository.findAllById(uniqueIds).stream()
                .collect(Collectors.toMap(MediaFile::getId, Function.identity()));

        Map<Long, MediaLink> latestLinkByMediaId = mediaLinkRepository
                .findByMediaIdInOrderByMediaIdAscCreatedAtDesc(uniqueIds)
                .stream()
                .collect(Collectors.toMap(MediaLink::getMediaId, Function.identity(), (left, right) -> left));

        Map<Long, Map<MediaDerivativeProfile, MediaDerivative>> derivativeByMediaAndProfile =
                findLatestReadyDerivativesByMediaId(uniqueIds);

        return uniqueIds.stream()
                .map(mediaFileMap::get)
                .filter(Objects::nonNull)
                .filter(mediaFile -> isAccessibleAndReady(mediaFile, userId))
                .map(mediaFile -> toMediaUrlResponse(
                        mediaFile,
                        latestLinkByMediaId.get(mediaFile.getId()),
                        derivativeByMediaAndProfile.getOrDefault(mediaFile.getId(), Map.of())
                ))
                .toList();
    }

    private void validateAccess(MediaFile mediaFile, Long userId) {
        if (userId != null && mediaFile.getUploaderId() != null && !mediaFile.isOwnedBy(userId)) {
            throw new BusinessException(MediaErrorCode.FORBIDDEN_MEDIA_ACCESS);
        }
    }

    private void validateStatus(MediaFile mediaFile) {
        if (mediaFile.getStatus() != MediaStatus.CONFIRMED && mediaFile.getStatus() != MediaStatus.READY) {
            throw new BusinessException(MediaErrorCode.MEDIA_NOT_READY);
        }
    }

    private boolean isAccessibleAndReady(MediaFile mediaFile, Long userId) {
        try {
            validateAccess(mediaFile, userId);
            validateStatus(mediaFile);
            return true;
        } catch (BusinessException ignored) {
            return false;
        }
    }

    private Map<Long, Map<MediaDerivativeProfile, MediaDerivative>> findLatestReadyDerivativesByMediaId(List<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return Map.of();
        }

        List<MediaDerivative> readyDerivatives = mediaDerivativeRepository
                .findByMediaIdInAndDerivativeProfileInAndStatusOrderByMediaIdAscMediaVersionDescCreatedAtDesc(
                        mediaIds,
                        SERVING_DERIVATIVE_PROFILES,
                        MediaDerivativeStatus.READY
                );

        Map<Long, Map<MediaDerivativeProfile, MediaDerivative>> result = new LinkedHashMap<>();
        for (MediaDerivative derivative : readyDerivatives) {
            Map<MediaDerivativeProfile, MediaDerivative> derivativeByProfile = result.computeIfAbsent(
                    derivative.getMediaId(),
                    ignored -> new LinkedHashMap<>()
            );
            derivativeByProfile.putIfAbsent(derivative.getDerivativeProfile(), derivative);
        }
        return result;
    }

    private MediaUrlResponse toMediaUrlResponse(MediaFile mediaFile,
                                                MediaLink link,
                                                Map<MediaDerivativeProfile, MediaDerivative> derivativeByProfile) {
        String resolvedObjectKey = resolveObjectKey(mediaFile, link, derivativeByProfile);
        MediaUsageType resolvedUsageType = resolveUsageType(link);
        MediaUrlPolicyService.MediaUrlContract urlContract = mediaUrlPolicyService.resolve(
                resolvedObjectKey,
                resolvedUsageType
        );
        return MediaUrlResponse.builder()
                .mediaId(mediaFile.getId())
                .status(mediaFile.getStatus().name())
                .objectKey(resolvedObjectKey)
                .mediaUrl(urlContract.url())
                .urlAccessType(urlContract.accessType().name())
                .urlExpiresAt(urlContract.expiresAt())
                .cacheControl(urlContract.cacheControl())
                .usageType(resolvedUsageType != null ? resolvedUsageType.name() : null)
                .build();
    }

    private String resolveObjectKey(MediaFile mediaFile,
                                    MediaLink link,
                                    Map<MediaDerivativeProfile, MediaDerivative> derivativeByProfile) {
        MediaDerivative preferredDerivative = resolvePreferredDerivative(link, derivativeByProfile);
        if (preferredDerivative != null) {
            return preferredDerivative.getObjectKey();
        }
        return mediaFile.getObjectKey();
    }

    private MediaUsageType resolveUsageType(MediaLink link) {
        if (link != null) {
            return link.getUsageType();
        }
        return null;
    }

    private MediaDerivative resolvePreferredDerivative(MediaLink link,
                                                       Map<MediaDerivativeProfile, MediaDerivative> derivativeByProfile) {
        if (derivativeByProfile == null || derivativeByProfile.isEmpty()) {
            return null;
        }

        MediaUsageType usageType = resolveUsageType(link);
        if (usageType == MediaUsageType.THUMBNAIL) {
            MediaDerivative thumbnail = derivativeByProfile.get(THUMBNAIL_PROFILE);
            if (thumbnail != null) {
                return thumbnail;
            }
        }

        return derivativeByProfile.get(DISPLAY_PROFILE);
    }
}
