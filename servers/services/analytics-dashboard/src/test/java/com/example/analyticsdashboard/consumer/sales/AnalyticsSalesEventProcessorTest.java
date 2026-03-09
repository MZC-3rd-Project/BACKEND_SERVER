package com.example.analyticsdashboard.consumer.sales;

import com.example.analyticsdashboard.service.ingest.AnalyticsEventIngestService;
import com.example.config.kafka.IdempotentConsumerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsSalesEventProcessorTest {

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Mock
    private AnalyticsEventIngestService analyticsEventIngestService;

    private AnalyticsSalesEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new AnalyticsSalesEventProcessor(idempotentConsumerService, analyticsEventIngestService);
    }

    @Test
    void process_ingestsSupportedEvent() {
        String message = """
                {
                  "eventId": "evt-sales-1",
                  "eventType": "PURCHASE_CREATED"
                }
                """;
        stubIdempotent("evt-sales-1", "ANALYTICS_SALES_EVENT");

        processor.process(message, "evt-sales-1", "PURCHASE_CREATED");

        verify(analyticsEventIngestService).ingestSalesEvent(any(AnalyticsSalesEventMessage.class));
    }

    @Test
    void process_ignoresUnsupportedType() {
        String message = """
                {
                  "eventId": "evt-sales-2",
                  "eventType": "PURCHASE_UNKNOWN"
                }
                """;

        processor.process(message, "evt-sales-2", "PURCHASE_UNKNOWN");

        verifyNoInteractions(idempotentConsumerService);
        verifyNoInteractions(analyticsEventIngestService);
    }

    private void stubIdempotent(String eventId, String eventType) {
        when(idempotentConsumerService.executeIdempotent(eq(eventId), eq(eventType), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    supplier.get();
                    return Optional.empty();
                });
    }
}
