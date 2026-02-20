package com.example.search.service.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchMetricsService {

    private final MeterRegistry meterRegistry;
    private final AtomicLong kafkaLagMax = new AtomicLong(-1L);

    @Value("${search.metrics.alert.search-latency-ms:1500}")
    private long searchLatencyAlertMs;

    @Value("${search.metrics.runbook-url:https://runbook.example/search}")
    private String runbookUrl;

    @PostConstruct
    void registerGauges() {
        meterRegistry.gauge("search.kafka.lag.max", kafkaLagMax);
    }

    public void recordSearchLatency(Duration duration, boolean success, boolean timeout) {
        Timer.builder("search.api.latency")
                .description("Search API latency")
                .register(meterRegistry)
                .record(duration);

        if (!success) {
            meterRegistry.counter("search.api.errors", "type", timeout ? "timeout" : "error").increment();
        }

        if (duration.toMillis() >= searchLatencyAlertMs) {
            log.warn("[SearchSLA] high-latency detected. latencyMs={}, runbook={}", duration.toMillis(), runbookUrl);
        }
    }

    public void recordIndexingEvent(String eventType, boolean success) {
        meterRegistry.counter(
                "search.indexing.events",
                "eventType", eventType == null ? "UNKNOWN" : eventType,
                "result", success ? "success" : "failure"
        ).increment();
    }

    public void updateKafkaLag(long lag) {
        kafkaLagMax.set(Math.max(lag, -1L));
    }
}
