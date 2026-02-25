package com.example.gateway.bff.service;

import com.example.gateway.bff.dto.catalog.CatalogItemsDataResponse;
import com.example.gateway.bff.dto.catalog.CatalogItemsResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogMetricsServiceTest {

    private SimpleMeterRegistry meterRegistry;
    private CatalogMetricsService metricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new CatalogMetricsService(meterRegistry);
    }

    @Test
    void recordQueryCompleted_recordsLatencyAndEmptyCounter() {
        CatalogItemsResponse body = CatalogItemsResponse.success(
                new CatalogItemsDataResponse(List.of(), null, 0L)
        );

        metricsService.recordQueryCompleted(Duration.ofMillis(25), ResponseEntity.ok(body));

        Timer timer = meterRegistry.find("catalog.query.latency").timer();
        Counter emptyCounter = meterRegistry.find("catalog.query.empty").counter();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(emptyCounter).isNotNull();
        assertThat(emptyCounter.count()).isEqualTo(1.0);
    }

    @Test
    void recordQueryCompleted_recordsErrorOnNon2xx() {
        metricsService.recordQueryCompleted(
                Duration.ofMillis(12),
                ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(null)
        );

        Counter errorCounter = meterRegistry.find("catalog.query.error")
                .tags("type", "downstream")
                .counter();

        assertThat(errorCounter).isNotNull();
        assertThat(errorCounter.count()).isEqualTo(1.0);
    }

    @Test
    void recordFallbackCounters_areRecorded() {
        metricsService.recordDegradeFallback("primary_exception");
        metricsService.recordDetailFallback("HOT_DEAL", "hot_deal_404");

        Counter degradeCounter = meterRegistry.find("catalog.query.fallback.count")
                .tags("reason", "primary_exception")
                .counter();
        Counter detailCounter = meterRegistry.find("catalog.detail.fallback.count")
                .tags("salesChannel", "HOT_DEAL", "reason", "hot_deal_404")
                .counter();

        assertThat(degradeCounter).isNotNull();
        assertThat(degradeCounter.count()).isEqualTo(1.0);
        assertThat(detailCounter).isNotNull();
        assertThat(detailCounter.count()).isEqualTo(1.0);
    }
}
