package com.example.config.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@RequiredArgsConstructor
public class DeadLetterRetryScheduler {

    private final DeadLetterRecoveryService deadLetterRecoveryService;

    @Scheduled(fixedDelayString = "${app.kafka.dlq.retry-fixed-delay-ms:30000}")
    public void recover() {
        try {
            deadLetterRecoveryService.recover();
        } catch (Exception exception) {
            log.warn("Dead letter recovery scheduler failed", exception);
        }
    }
}
