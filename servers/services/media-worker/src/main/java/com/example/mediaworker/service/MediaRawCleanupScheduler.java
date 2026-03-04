package com.example.mediaworker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaRawCleanupScheduler {

    private final MediaRawCleanupService mediaRawCleanupService;

    @Scheduled(fixedDelayString = "${media.worker.raw-cleanup.fixed-delay-ms:3600000}")
    public void cleanupRawAfterTransition() {
        MediaRawCleanupService.CleanupResult result = mediaRawCleanupService.cleanupRawAfterTransition();
        if (result.scannedCount() > 0 || result.deleteFailedCount() > 0) {
            log.info(
                    "[MediaWorker][RawCleanup] scanned={}, purged={}, deletedObjects={}, deleteFailed={}, skippedDerivativeNotReady={}, skippedGrace={}",
                    result.scannedCount(),
                    result.purgedCount(),
                    result.deletedObjectCount(),
                    result.deleteFailedCount(),
                    result.skippedDerivativeNotReadyCount(),
                    result.skippedGraceCount()
            );
        }
    }
}
