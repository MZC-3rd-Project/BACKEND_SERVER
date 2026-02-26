package com.example.search.service.reconciliation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockReconciliationScheduler {

    private final StockReconciliationService stockReconciliationService;

    @Value("${search.reconciliation.enabled:false}")
    private boolean reconciliationEnabled;

    @Value("${search.reconciliation.sample-item-ids:}")
    private String sampleItemIds;

    @Scheduled(fixedDelayString = "${search.reconciliation.interval-ms:300000}")
    public void reconcileSampleItems() {
        if (!reconciliationEnabled || !StringUtils.hasText(sampleItemIds)) {
            return;
        }

        String[] tokens = sampleItemIds.split(",");
        for (String token : tokens) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            try {
                Long itemId = Long.parseLong(token.trim());
                stockReconciliationService.reconcileItem(itemId);
            } catch (NumberFormatException e) {
                log.warn("[StockReconcile] invalid sample item id. raw={}", token);
            }
        }
    }
}
