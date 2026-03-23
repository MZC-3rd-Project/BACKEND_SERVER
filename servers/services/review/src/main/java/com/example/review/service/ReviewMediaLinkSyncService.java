package com.example.review.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewMediaLinkSyncService {

    private final ReviewMediaService reviewMediaService;
    private final ReviewMediaLinkSyncRetryService retryService;

    public void syncAfterCommit(Long reviewId, List<Long> mediaIds) {
        List<Long> safeMediaIds = mediaIds == null ? List.of() : mediaIds;
        if (reviewId == null || reviewId <= 0 || safeMediaIds.isEmpty()) {
            return;
        }

        Runnable syncAction = () -> executeSync(reviewId, safeMediaIds);
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

    private void executeSync(Long reviewId, List<Long> mediaIds) {
        try {
            reviewMediaService.syncReviewImages(reviewId, mediaIds);
            retryService.markCompleted(reviewId, mediaIds);
        } catch (Exception e) {
            log.error("Review media link sync failed after commit: reviewId={}, gallerySize={}",
                    reviewId, mediaIds.size(), e);
            retryService.enqueue(reviewId, mediaIds, e.getMessage());
        }
    }
}
