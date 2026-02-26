package com.example.product.service.command.image;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemMediaLinkSyncService {

    private final MediaReferenceService mediaReferenceService;
    private final ItemMediaLinkSyncRetryService retryService;

    public void syncAfterCommit(Long itemId, Long thumbnailMediaId, List<Long> galleryMediaIds) {
        if (itemId == null || itemId <= 0) {
            return;
        }
        Runnable syncAction = () -> executeSync(itemId, thumbnailMediaId, galleryMediaIds);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    syncAction.run();
                }
            });
            return;
        }
        syncAction.run();
    }

    public void clearAfterCommit(Long itemId) {
        syncAfterCommit(itemId, null, List.of());
    }

    private void executeSync(Long itemId, Long thumbnailMediaId, List<Long> galleryMediaIds) {
        List<Long> safeGalleryMediaIds = galleryMediaIds == null ? List.of() : galleryMediaIds;
        try {
            mediaReferenceService.syncItemMediaLinks(itemId, thumbnailMediaId, safeGalleryMediaIds);
            retryService.markCompleted(itemId, thumbnailMediaId, safeGalleryMediaIds);
        } catch (Exception e) {
            log.error("Item media link sync failed after commit: itemId={}, thumbnailMediaId={}, gallerySize={}",
                    itemId, thumbnailMediaId, safeGalleryMediaIds.size(), e);
            retryService.enqueue(itemId, thumbnailMediaId, safeGalleryMediaIds, e.getMessage());
        }
    }
}
