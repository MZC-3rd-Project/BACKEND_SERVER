package com.example.analyticsdashboard.consumer.search;

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
class AnalyticsSearchEventProcessorTest {

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Mock
    private AnalyticsEventIngestService analyticsEventIngestService;

    private AnalyticsSearchEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new AnalyticsSearchEventProcessor(idempotentConsumerService, analyticsEventIngestService);
    }

    @Test
    void process_ingestsSupportedEvent() {
        String message = """
                {
                  "eventId": "evt-search-1",
                  "eventType": "SEARCH_EXECUTED"
                }
                """;
        stubIdempotent("evt-search-1", "ANALYTICS_SEARCH_EVENT");

        processor.process(message, "evt-search-1", "SEARCH_EXECUTED");

        verify(analyticsEventIngestService).ingestSearchEvent(any(AnalyticsSearchEventMessage.class));
    }

    @Test
    void process_ignoresUnsupportedType() {
        String message = """
                {
                  "eventId": "evt-search-2",
                  "eventType": "SEARCH_UNKNOWN"
                }
                """;

        processor.process(message, "evt-search-2", "SEARCH_UNKNOWN");

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
