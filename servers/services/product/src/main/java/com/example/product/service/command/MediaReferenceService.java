package com.example.product.service.command;

import com.example.core.exception.BusinessException;
import com.example.product.client.MediaClient;
import com.example.product.exception.ProductErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MediaReferenceService {

    private final MediaClient mediaClient;

    public String resolveMediaUrl(Long mediaId) {
        if (mediaId == null) {
            return null;
        }
        return mediaClient.getMediaUrl(mediaId);
    }

    public Map<Long, String> resolveMediaUrlMap(Collection<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new LinkedHashMap<>();
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(mediaIds);
        for (Long mediaId : uniqueIds) {
            if (mediaId == null) {
                throw new BusinessException(ProductErrorCode.INVALID_MEDIA_REFERENCE);
            }
            result.put(mediaId, resolveMediaUrl(mediaId));
        }
        return result;
    }

    public void syncItemMediaLinks(Long itemId, Long thumbnailMediaId, List<Long> galleryMediaIds) {
        mediaClient.syncItemLinks(itemId, thumbnailMediaId, galleryMediaIds);
    }
}
