package com.example.review.scheduler;

import com.example.review.service.ReviewMediaLinkSyncRetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewMediaLinkSyncRetryScheduler {

    private final ReviewMediaLinkSyncRetryService retryService;

    @Scheduled(fixedDelayString = "${app.media-link-sync-retry.fixed-delay-ms:30000}")
    public void processRetries() {
        try {
            retryService.processDueRetries();
        } catch (Exception e) {
            log.error("Review media link sync retry scheduler failed", e);
        }
    }
}
