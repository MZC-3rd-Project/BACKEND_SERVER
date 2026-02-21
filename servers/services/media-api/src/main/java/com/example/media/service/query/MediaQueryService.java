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
}
