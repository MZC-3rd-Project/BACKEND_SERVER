package com.example.search.service.thumbnail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchThumbnailEnrichmentScheduler {

    private final SearchThumbnailEnrichmentTaskService thumbnailEnrichmentTaskService;

    @Value("${search.thumbnail-enricher.enabled:false}")
    private boolean thumbnailEnricherEnabled;

    @Scheduled(fixedDelayString = "${search.thumbnail-enricher.fixed-delay-ms:15000}")
    public void processPendingTasks() {
        if (!thumbnailEnricherEnabled) {
            return;
        }
        try {
            thumbnailEnrichmentTaskService.processDueRetries();
        } catch (Exception e) {
            log.error("[SearchThumbnailEnricher] scheduler execution failed", e);
        }
    }
}
