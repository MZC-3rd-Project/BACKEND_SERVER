package com.example.notification.service.delivery;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDeliveryRetryScheduler {

    private final NotificationDeliveryService notificationDeliveryService;

    @Scheduled(fixedDelayString = "${notification.delivery.retry-fixed-delay-ms:5000}")
    public void retryDispatch() {
        try {
            notificationDeliveryService.dispatchRetryTargets();
        } catch (Exception e) {
            Counter.builder("notification.delivery.retry.scheduler.errors.total")
                    .register(Metrics.globalRegistry)
                    .increment();
            log.warn("Notification delivery retry scheduler failed", e);
        }
    }
}
