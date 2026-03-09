package com.example.analyticsdashboard.consumer.item;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsItemEventProcessorTest {

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Mock
    private AnalyticsEventIngestService analyticsEventIngestService;

    private AnalyticsItemEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new AnalyticsItemEventProcessor(idempotentConsumerService, analyticsEventIngestService);
    }

    @Test
    void process_ingestsSupportedEvent() {
        String message = """
                {
                  "eventId": "evt-item-1",
                  "eventType": "ITEM_CREATED",
                  "itemId": 101
                }
                """;
        stubIdempotent("evt-item-1", "ANALYTICS_ITEM_EVENT");

        processor.process(message, "evt-item-1", "ITEM_CREATED");

        verify(analyticsEventIngestService).ingestItemEvent(any(AnalyticsItemEventMessage.class));
    }

    @Test
    void process_skipsInvalidPayload() {
        String message = """
                {
                  "eventId": "evt-item-2",
                  "eventType": "ITEM_CREATED"
                }
                """;

        processor.process(message, "evt-item-2", "ITEM_CREATED");

        verifyNoInteractions(idempotentConsumerService);
        verifyNoInteractions(analyticsEventIngestService);
    }

    @Test
    void process_rethrowsWhenIdempotentFails() {
        String message = """
                {
                  "eventId": "evt-item-3",
                  "eventType": "ITEM_CREATED",
                  "itemId": 101
                }
                """;
        when(idempotentConsumerService.executeIdempotent(eq("evt-item-3"), eq("ANALYTICS_ITEM_EVENT"), any()))
                .thenThrow(new RuntimeException("boom"));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> processor.process(message, "evt-item-3", "ITEM_CREATED"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("boom");

        verify(analyticsEventIngestService, never()).ingestItemEvent(any(AnalyticsItemEventMessage.class));
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
