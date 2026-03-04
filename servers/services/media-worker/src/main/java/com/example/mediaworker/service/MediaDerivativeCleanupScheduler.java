package com.example.mediaworker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaDerivativeCleanupScheduler {

    private final MediaDerivativeCleanupService mediaDerivativeCleanupService;

    @Scheduled(fixedDelayString = "${media.worker.cleanup.fixed-delay-ms:60000}")
    public void cleanupOrphanedDerivatives() {
        MediaDerivativeCleanupService.CleanupResult result = mediaDerivativeCleanupService.cleanupOrphanedDerivatives();
        if (result.orphanCount() > 0 || result.deleteFailedCount() > 0) {
            log.info(
                    "[MediaWorker][Cleanup] orphan={}, purged={}, deletedObjects={}, deleteFailed={}",
                    result.orphanCount(),
                    result.purgedCount(),
                    result.deletedObjectCount(),
                    result.deleteFailedCount()
            );
        }
    }
}
