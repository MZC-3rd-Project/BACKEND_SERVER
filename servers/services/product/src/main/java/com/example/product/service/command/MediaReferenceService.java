package com.example.product.service.command;

import com.example.core.exception.BusinessException;
import com.example.clients.media.InvalidMediaReferenceException;
import com.example.clients.media.MediaClientException;
import com.example.clients.media.MediaClientFacade;
import com.example.clients.media.MediaLinksSyncCommand;
import com.example.product.exception.ProductErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaReferenceService {

    private final MediaClientFacade mediaClientFacade;

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
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(mediaIds);
        for (Long mediaId : uniqueIds) {
            if (mediaId == null) {
                throw new BusinessException(ProductErrorCode.INVALID_MEDIA_REFERENCE);
            }
            resolveMediaUrl(mediaId);
        }
    }

    public void syncItemMediaLinks(Long itemId, Long thumbnailMediaId, List<Long> galleryMediaIds) {
        try {
            mediaClientFacade.syncLinks(new MediaLinksSyncCommand(
                    "ITEM",
                    itemId,
                    List.of(
                            new MediaLinksSyncCommand.MediaUsageSet(
                                    "THUMBNAIL",
                                    thumbnailMediaId == null ? List.of() : List.of(thumbnailMediaId)
                            ),
                            new MediaLinksSyncCommand.MediaUsageSet(
                                    "GALLERY",
                                    galleryMediaIds == null ? List.of() : galleryMediaIds
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
