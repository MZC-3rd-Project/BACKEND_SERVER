package com.example.product.service.command;

import com.example.core.exception.BusinessException;
import com.example.clients.media.InvalidMediaReferenceException;
import com.example.clients.media.MediaClientException;
import com.example.clients.media.MediaClientFacade;
import com.example.clients.media.MediaClientValidator;
import com.example.clients.media.MediaLinksSyncCommand;
import com.example.product.exception.ProductErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaReferenceService {

    private final MediaClientFacade mediaClientFacade;
    private final MediaClientValidator mediaClientValidator;

    public String resolveMediaUrl(Long mediaId) {
        if (mediaId == null) {
            return null;
        }
        try {
            return mediaClientFacade.getMediaUrl(mediaId);
        } catch (InvalidMediaReferenceException e) {
            throw new BusinessException(ProductErrorCode.INVALID_MEDIA_REFERENCE, e);
        } catch (MediaClientException e) {
            throw new BusinessException(ProductErrorCode.MEDIA_SERVICE_ERROR, e);
        }
    }

    public void validateMediaReferences(Collection<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return;
        }
        try {
            List<Long> uniqueIds = mediaClientValidator.normalizeMediaIds(mediaIds);
            for (Long mediaId : uniqueIds) {
                resolveMediaUrl(mediaId);
            }
        } catch (InvalidMediaReferenceException e) {
            throw new BusinessException(ProductErrorCode.INVALID_MEDIA_REFERENCE, e);
        }
    }

    public void syncItemMediaLinks(Long itemId, Long thumbnailMediaId, List<Long> galleryMediaIds) {
        try {
            List<Long> thumbnailMediaIds = List.of();
            if (thumbnailMediaId != null) {
                mediaClientValidator.validateMediaId(thumbnailMediaId);
                thumbnailMediaIds = List.of(thumbnailMediaId);
            }
            List<Long> normalizedGalleryMediaIds = mediaClientValidator.normalizeMediaIds(galleryMediaIds);

            mediaClientFacade.syncLinks(new MediaLinksSyncCommand(
                    "ITEM",
                    itemId,
                    List.of(
                            new MediaLinksSyncCommand.MediaUsageSet(
                                    "THUMBNAIL",
                                    thumbnailMediaIds
                            ),
                            new MediaLinksSyncCommand.MediaUsageSet(
                                    "GALLERY",
                                    normalizedGalleryMediaIds
                            )
                    )
            ));
        } catch (InvalidMediaReferenceException e) {
            throw new BusinessException(ProductErrorCode.INVALID_MEDIA_REFERENCE, e);
        } catch (MediaClientException e) {
            throw new BusinessException(ProductErrorCode.MEDIA_SERVICE_ERROR, e);
        }
    }
}
