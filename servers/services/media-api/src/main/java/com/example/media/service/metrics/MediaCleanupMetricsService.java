package com.example.media.service.metrics;

import com.example.media.service.command.MediaCommandService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaCleanupMetricsService {

    private final MeterRegistry meterRegistry;

    public void record(MediaCommandService.ExpireResult result, Duration duration) {
        Timer.builder("media.cleanup.duration")
                .description("Media cleanup scheduler duration")
                .register(meterRegistry)
                .record(duration);

        incrementIfPositive("media.cleanup.expired.total", result.expiredCount());
        incrementIfPositive("media.cleanup.s3.delete.total", "result", "success", result.deletedObjectCount());
        incrementIfPositive("media.cleanup.s3.delete.total", "result", "failure", result.deleteFailedCount());

        if (result.deleteFailedCount() > 0) {
            meterRegistry.counter("media.cleanup.alerts.total", "type", "delete_failure").increment();
            log.warn(
                    "[MediaCleanupAlert] delete failures detected. deleteFailed={}, expired={}",
                    result.deleteFailedCount(),
                    result.expiredCount()
            );
        }
    }

    private void incrementIfPositive(String metricName, int count) {
        if (count <= 0) {
            return;
        }
        meterRegistry.counter(metricName).increment(count);
    }

    private void incrementIfPositive(String metricName, String tagKey, String tagValue, int count) {
        if (count <= 0) {
            return;
        }
        meterRegistry.counter(metricName, tagKey, tagValue).increment(count);
    }
}
