package com.example.media.service.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaCleanupScheduler {

    private final MediaCommandService mediaCommandService;

    @Scheduled(cron = "${media.cleanup.cron:0 */10 * * * *}")
    public void expirePendingUploads() {
        MediaCommandService.ExpireResult result = mediaCommandService.expirePendingUploads();
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
