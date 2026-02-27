package com.example.product.scheduler;

import com.example.product.service.command.image.ItemMediaLinkSyncRetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ItemMediaLinkSyncRetryScheduler {

    private final ItemMediaLinkSyncRetryService retryService;

    @Scheduled(fixedDelayString = "${app.media-link-sync-retry.fixed-delay-ms:30000}")
    public void processRetries() {
        try {
            retryService.processDueRetries();
        } catch (Exception e) {
            log.error("Item media link sync retry scheduler failed", e);
        }
    }
}
