package com.example.sales.scheduler;

import com.example.sales.service.retry.StockCancelRetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockCancelRetryScheduler {

    private final StockCancelRetryService stockCancelRetryService;

    @Scheduled(fixedDelay = 30000)
    public void processRetries() {
        try {
            stockCancelRetryService.processDueRetries();
        } catch (Exception e) {
            log.error("Sales stock cancel retry scheduler failed", e);
        }
    }
}
