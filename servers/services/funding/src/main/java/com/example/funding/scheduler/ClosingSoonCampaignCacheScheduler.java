package com.example.funding.scheduler;

import com.example.funding.service.cache.ClosingSoonCampaignCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClosingSoonCampaignCacheScheduler {

    private final ClosingSoonCampaignCacheService closingSoonCampaignCacheService;

    @Scheduled(initialDelay = 30000, fixedDelay = 300000)
    public void refreshClosingSoonCampaigns() {
        try {
            int refreshedSize = closingSoonCampaignCacheService.refreshDefaultCache().getSize();
            log.debug("closing soon funding campaigns refreshed. size={}", refreshedSize);
        } catch (Exception e) {
            log.warn("closing soon funding cache refresh failed", e);
        }
    }
}
