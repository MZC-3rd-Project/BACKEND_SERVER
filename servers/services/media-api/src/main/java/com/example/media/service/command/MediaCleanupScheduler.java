package com.example.media.service.command;

import com.example.media.service.metrics.MediaCleanupMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaCleanupScheduler {

    private final MediaCommandService mediaCommandService;
    private final MediaCleanupMetricsService mediaCleanupMetricsService;

    @Scheduled(cron = "${media.cleanup.cron:0 */10 * * * *}")
    public void expirePendingUploads() {
        long startNanos = System.nanoTime();
        MediaCommandService.ExpireResult result = mediaCommandService.expirePendingUploads();
        mediaCleanupMetricsService.record(
                result,
                Duration.ofNanos(System.nanoTime() - startNanos)
        );
        if (result.expiredCount() > 0 || result.deleteFailedCount() > 0) {
            log.info(
                    "[MediaCleanup] expired={}, deletedObjects={}, deleteFailed={}",
                    result.expiredCount(),
                    result.deletedObjectCount(),
                    result.deleteFailedCount()
            );
        }
    }
}
