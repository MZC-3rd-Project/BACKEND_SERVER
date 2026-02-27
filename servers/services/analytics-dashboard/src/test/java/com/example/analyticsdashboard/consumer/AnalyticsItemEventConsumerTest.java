package com.example.analyticsdashboard.consumer;

import com.example.analyticsdashboard.service.ingest.AnalyticsEventIngestService;
import com.example.config.kafka.IdempotentConsumerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsItemEventConsumerTest {

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Mock
    private AnalyticsEventIngestService analyticsEventIngestService;

    @InjectMocks
    private AnalyticsItemEventConsumer consumer;

    @Test
    void consume_validEvent_executesIngest() {
        stubIdempotentExecution();
        String message = """
                {
                  "eventId": "evt-item-1",
                  "eventType": "ITEM_CREATED",
                  "itemId": 101,
                  "storeId": 201,
                  "sellerId": 301
                }
                """;

        consumer.consume(message);

        verify(idempotentConsumerService).executeIdempotent(anyString(), anyString(), any());
        verify(analyticsEventIngestService).ingestItemEvent(any(AnalyticsItemEventMessage.class));
    }

    @Test
    void consume_invalidEvent_skipsIdempotent() {
        String message = """
                {
                  "eventType": "ITEM_CREATED",
                  "itemId": 101
                }
                """;

        consumer.consume(message);

        verify(idempotentConsumerService, never()).executeIdempotent(anyString(), anyString(), any());
        verify(analyticsEventIngestService, never()).ingestItemEvent(any(AnalyticsItemEventMessage.class));
    }

    private void stubIdempotentExecution() {
        when(idempotentConsumerService.executeIdempotent(anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<Object> supplier = invocation.getArgument(2);
                    return Optional.ofNullable(supplier.get());
                });
    }
}
