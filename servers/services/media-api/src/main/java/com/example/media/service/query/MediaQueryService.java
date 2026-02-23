package com.example.media.service.query;

import com.example.core.exception.BusinessException;
import com.example.media.dto.query.response.MediaUrlResponse;
import com.example.media.entity.MediaFile;
import com.example.media.entity.MediaLink;
import com.example.media.entity.MediaStatus;
import com.example.media.exception.MediaErrorCode;
import com.example.media.repository.MediaFileRepository;
import com.example.media.repository.MediaLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MediaQueryService {

    private final MediaFileRepository mediaFileRepository;
    private final MediaLinkRepository mediaLinkRepository;
    private final MediaUrlPolicyService mediaUrlPolicyService;

    @Transactional(readOnly = true)
    public MediaUrlResponse getMediaUrl(Long mediaId, Long userId) {
        MediaFile mediaFile = mediaFileRepository.findById(mediaId)
                .orElseThrow(() -> new BusinessException(MediaErrorCode.MEDIA_NOT_FOUND));

        validateAccess(mediaFile, userId);
        validateStatus(mediaFile);

        MediaLink link = mediaLinkRepository.findTopByMediaIdOrderByCreatedAtDesc(mediaId).orElse(null);
        return toMediaUrlResponse(mediaFile, link);
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

        return uniqueIds.stream()
                .map(mediaFileMap::get)
                .filter(Objects::nonNull)
                .filter(mediaFile -> isAccessibleAndReady(mediaFile, userId))
                .map(mediaFile -> toMediaUrlResponse(mediaFile, latestLinkByMediaId.get(mediaFile.getId())))
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

    private MediaUrlResponse toMediaUrlResponse(MediaFile mediaFile, MediaLink link) {
        MediaUrlPolicyService.MediaUrlContract urlContract = mediaUrlPolicyService.resolve(
                mediaFile.getObjectKey(),
                link != null ? link.getUsageType() : null
        );
        return MediaUrlResponse.builder()
                .mediaId(mediaFile.getId())
                .status(mediaFile.getStatus().name())
                .objectKey(mediaFile.getObjectKey())
                .mediaUrl(urlContract.url())
                .urlAccessType(urlContract.accessType().name())
                .urlExpiresAt(urlContract.expiresAt())
                .cacheControl(urlContract.cacheControl())
                .usageType(link != null ? link.getUsageType().name() : null)
                .build();
    }
}
