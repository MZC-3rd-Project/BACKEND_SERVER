package com.example.mediaworker.service.ops;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class MediaWorkerMetricsService {

    private final Counter taskSuccessCounter;
    private final Counter taskFailureCounter;
    private final Counter taskRetryScheduledCounter;
    private final Counter taskDlqCounter;
    private final Counter staleRecoveredCounter;
    private final Timer taskProcessingLatencyTimer;

    public MediaWorkerMetricsService(MeterRegistry meterRegistry) {
        this.taskSuccessCounter = Counter.builder("media.worker.tasks.success.total")
                .description("Total number of media derivative tasks completed successfully")
                .register(meterRegistry);
        this.taskFailureCounter = Counter.builder("media.worker.tasks.failure.total")
                .description("Total number of media derivative processing attempts failed")
                .register(meterRegistry);
        this.taskRetryScheduledCounter = Counter.builder("media.worker.tasks.retry.scheduled.total")
                .description("Total number of media derivative retries scheduled")
                .register(meterRegistry);
        this.taskDlqCounter = Counter.builder("media.worker.tasks.dlq.total")
                .description("Total number of media derivative tasks moved to DLQ")
                .register(meterRegistry);
        this.staleRecoveredCounter = Counter.builder("media.worker.tasks.stale.recovered.total")
                .description("Total number of stale PROCESSING tasks recovered")
                .register(meterRegistry);
        this.taskProcessingLatencyTimer = Timer.builder("media.worker.tasks.processing.latency")
                .description("Latency of media derivative task processing")
                .register(meterRegistry);
    }

    public void recordTaskSuccess(Duration latency) {
        if (latency != null && !latency.isNegative()) {
            taskProcessingLatencyTimer.record(latency);
        }
        taskSuccessCounter.increment();
    }

    public void recordTaskFailure(Duration latency) {
        if (latency != null && !latency.isNegative()) {
            taskProcessingLatencyTimer.record(latency);
        }
        taskFailureCounter.increment();
    }

    public void recordRetryScheduled() {
        taskRetryScheduledCounter.increment();
    }

    public void recordDlq() {
        taskDlqCounter.increment();
    }

    public void recordStaleRecovered(int count) {
        if (count <= 0) {
            return;
        }
        staleRecoveredCounter.increment(count);
    }
}
