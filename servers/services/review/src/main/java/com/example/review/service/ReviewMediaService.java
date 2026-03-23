package com.example.review.service;

import com.example.clients.media.dto.MediaLinksSyncCommand;
import com.example.clients.media.dto.MediaOwnerType;
import com.example.clients.media.dto.MediaUsageType;
import com.example.clients.media.exception.InvalidMediaReferenceException;
import com.example.clients.media.exception.MediaClientException;
import com.example.clients.media.facade.MediaClientFacade;
import com.example.core.exception.BusinessException;
import com.example.review.exception.ReviewErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReviewMediaService {

    private final MediaClientFacade mediaClientFacade;

    public List<Long> validateMediaIds(List<Long> imageMediaIds) {
        if (imageMediaIds == null || imageMediaIds.isEmpty()) {
            return List.of();
        }

        List<Long> normalized = imageMediaIds.stream()
                .filter(mediaId -> mediaId != null && mediaId > 0)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));

        try {
            for (Long mediaId : normalized) {
                mediaClientFacade.getMediaUrl(mediaId);
            }
            return normalized;
        } catch (InvalidMediaReferenceException e) {
            throw new BusinessException(ReviewErrorCode.INVALID_MEDIA_REFERENCE, e);
        } catch (MediaClientException e) {
            throw new BusinessException(ReviewErrorCode.MEDIA_SERVICE_ERROR, e);
        }
    }

    public Map<Long, String> resolveMediaUrls(List<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return Map.of();
        }
        try {
            return mediaClientFacade.getMediaUrlMap(mediaIds);
        } catch (MediaClientException e) {
            throw new BusinessException(ReviewErrorCode.MEDIA_SERVICE_ERROR, e);
        }
    }

    public void syncReviewImages(Long reviewId, List<Long> mediaIds) {
        try {
            mediaClientFacade.syncLinks(new MediaLinksSyncCommand(
                    MediaOwnerType.REVIEW,
                    reviewId,
                    List.of(new MediaLinksSyncCommand.MediaUsageSet(MediaUsageType.GALLERY, mediaIds))
            ));
        } catch (InvalidMediaReferenceException e) {
            throw new BusinessException(ReviewErrorCode.INVALID_MEDIA_REFERENCE, e);
        } catch (MediaClientException e) {
            throw new BusinessException(ReviewErrorCode.MEDIA_SERVICE_ERROR, e);
        }
    }
}
