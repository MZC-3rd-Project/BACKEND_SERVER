package com.example.product.service.command.image;

import com.example.product.entity.image.ItemImage;
import com.example.product.repository.ItemImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemThumbnailSyncService {

    private final ItemImageRepository itemImageRepository;
    private final ItemMediaLinkSyncService itemMediaLinkSyncService;

    public void syncAfterCommit(Long itemId, Long thumbnailMediaId) {
        syncAfterCommit(itemId, thumbnailMediaId, false);
    }

    public void syncAfterCommit(Long itemId, Long thumbnailMediaId, boolean forceClearWhenEmpty) {
        List<Long> galleryMediaIds = itemImageRepository.findByItemIdOrderBySortOrder(itemId).stream()
                .map(ItemImage::getMediaId)
                .filter(mediaId -> thumbnailMediaId == null || !thumbnailMediaId.equals(mediaId))
                .filter(Objects::nonNull)
                .toList();
        syncAfterCommit(itemId, thumbnailMediaId, galleryMediaIds, forceClearWhenEmpty);
    }

    public void syncAfterCommit(Long itemId, Long thumbnailMediaId, List<Long> galleryMediaIds) {
        syncAfterCommit(itemId, thumbnailMediaId, galleryMediaIds, true);
    }

    private void syncAfterCommit(
            Long itemId,
            Long thumbnailMediaId,
            List<Long> galleryMediaIds,
            boolean forceClearWhenEmpty
    ) {
        List<Long> safeGalleryMediaIds = galleryMediaIds == null ? List.of() : galleryMediaIds.stream()
                .filter(Objects::nonNull)
                .toList();

        if (thumbnailMediaId == null && safeGalleryMediaIds.isEmpty()) {
            if (forceClearWhenEmpty) {
                itemMediaLinkSyncService.clearAfterCommit(itemId);
                log.debug("Item thumbnail sync cleared after commit. itemId={}", itemId);
            }
            return;
        }

        itemMediaLinkSyncService.syncAfterCommit(itemId, thumbnailMediaId, safeGalleryMediaIds);
        log.debug("Item thumbnail sync scheduled after commit. itemId={}, thumbnailMediaId={}, galleryCount={}",
                itemId, thumbnailMediaId, safeGalleryMediaIds.size());
    }
}
