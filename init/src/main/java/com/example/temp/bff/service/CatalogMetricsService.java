package com.example.gateway.bff.service;

import com.example.gateway.bff.dto.catalog.CatalogItemsResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class CatalogMetricsService {

    private final MeterRegistry meterRegistry;

    public void recordQueryCompleted(Duration duration, ResponseEntity<CatalogItemsResponse> responseEntity) {
        Timer.builder("catalog.query.latency")
                .description("Catalog query latency")
                .register(meterRegistry)
                .record(duration);

        if (responseEntity == null || !responseEntity.getStatusCode().is2xxSuccessful()) {
            meterRegistry.counter("catalog.query.error", "type", "downstream").increment();
            return;
        }

        CatalogItemsResponse body = responseEntity.getBody();
        if (body == null || !body.success() || body.data() == null) {
            meterRegistry.counter("catalog.query.error", "type", "mapping").increment();
            return;
        }

        if (body.data().items() == null || body.data().items().isEmpty()) {
            meterRegistry.counter("catalog.query.empty").increment();
        }
    }

    public void recordQueryException(Duration duration, String type) {
        Timer.builder("catalog.query.latency")
                .description("Catalog query latency")
                .register(meterRegistry)
                .record(duration);
        meterRegistry.counter("catalog.query.error", "type", safe(type, "exception")).increment();
    }

    public void recordDegradeFallback(String reason) {
        meterRegistry.counter("catalog.query.fallback.count", "reason", safe(reason, "unknown")).increment();
    }

    public void recordDetailFallback(String salesChannel, String reason) {
        meterRegistry.counter(
                "catalog.detail.fallback.count",
                "salesChannel", safe(salesChannel, "UNKNOWN"),
                "reason", safe(reason, "unknown")
        ).increment();
    }

    private String safe(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
