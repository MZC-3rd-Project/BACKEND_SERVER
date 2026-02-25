package com.example.search.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.search.service.index.SearchIndexingFailureService;
import com.example.search.service.index.SearchIndexingService;
import com.example.search.service.metrics.SearchMetricsService;
import com.example.search.service.query.cache.SearchResultCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotDealEventConsumerTest {

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Mock
    private SearchIndexingService searchIndexingService;

    @Mock
    private SearchResultCacheService searchResultCacheService;

    @Mock
    private SearchIndexingFailureService searchIndexingFailureService;

    @Mock
    private SearchMetricsService searchMetricsService;

    @InjectMocks
    private HotDealEventConsumer hotDealEventConsumer;

    @Test
    void consume_routesHotDealStartedToProjectionUpdate() {
        stubIdempotentExecution();

        String message = """
                {
                  "eventId": "evt-hot-1",
                  "eventType": "HOT_DEAL_STARTED",
                  "hotDealId": 9001,
                  "itemId": 101,
                  "discountedPrice": 9900
                }
                """;

        hotDealEventConsumer.consume(message);

        verify(searchIndexingService).applyHotDealStartedByEventTime(101L, 9001L, 9900L, null);
        verify(searchResultCacheService).evictAll();
    }

    @Test
    void consume_routesHotDealEndedToProjectionUpdate() {
        stubIdempotentExecution();

        String message = """
                {
                  "eventId": "evt-hot-2",
                  "eventType": "HOT_DEAL_ENDED",
                  "hotDealId": 9001,
                  "itemId": 101
                }
                """;

        hotDealEventConsumer.consume(message);

        verify(searchIndexingService).applyHotDealEndedByEventTime(101L, 9001L, null);
        verify(searchResultCacheService).evictAll();
    }

    @Test
    void consume_ignoresInvalidEventWithoutIdempotentExecution() {
        String message = """
                {
                  "eventType": "HOT_DEAL_STARTED",
                  "itemId": 101
                }
                """;

        hotDealEventConsumer.consume(message);

        verify(idempotentConsumerService, never()).executeIdempotent(anyString(), anyString(), any());
        verify(searchIndexingService, never()).applyHotDealStartedByEventTime(any(), any(), any(), isNull());
        verify(searchIndexingService, never()).applyHotDealEndedByEventTime(any(), any(), isNull());
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
