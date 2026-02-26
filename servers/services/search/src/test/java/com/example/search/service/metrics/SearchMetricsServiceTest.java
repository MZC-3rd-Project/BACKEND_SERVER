package com.example.search.service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchMetricsServiceTest {

    private SimpleMeterRegistry meterRegistry;
    private SearchMetricsService searchMetricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        searchMetricsService = new SearchMetricsService(meterRegistry);
    }

    @Test
    void recordIndexingEvent_recordsCatalogProjectionMetricsOnSuccess() {
        searchMetricsService.recordIndexingEvent("HOT_DEAL_STARTED", true);

        Counter projectionCounter = meterRegistry.find("catalog.projection.events")
                .tags("eventType", "HOT_DEAL_STARTED", "result", "success")
                .counter();
        Counter failureCounter = meterRegistry.find("catalog.projection.failures")
                .tags("eventType", "HOT_DEAL_STARTED")
                .counter();

        assertThat(projectionCounter).isNotNull();
        assertThat(projectionCounter.count()).isEqualTo(1.0);
        assertThat(failureCounter).isNull();
    }

    @Test
    void recordIndexingEvent_recordsCatalogProjectionFailureMetricOnFailure() {
        searchMetricsService.recordIndexingEvent("FUNDING_FAILED", false);

        Counter projectionCounter = meterRegistry.find("catalog.projection.events")
                .tags("eventType", "FUNDING_FAILED", "result", "failure")
                .counter();
        Counter failureCounter = meterRegistry.find("catalog.projection.failures")
                .tags("eventType", "FUNDING_FAILED")
                .counter();

        assertThat(projectionCounter).isNotNull();
        assertThat(projectionCounter.count()).isEqualTo(1.0);
        assertThat(failureCounter).isNotNull();
        assertThat(failureCounter.count()).isEqualTo(1.0);
    }
}
