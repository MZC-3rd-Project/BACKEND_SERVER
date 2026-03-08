package com.example.search.consumer.hotdeal;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.search.service.index.SearchIndexingFailureService;
import com.example.search.service.index.SearchIndexingService;
import com.example.search.service.metrics.SearchMetricsService;
import com.example.search.service.query.cache.SearchResultCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class SearchHotDealEventProcessorTest {

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

    private SearchHotDealEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new SearchHotDealEventProcessor(
                idempotentConsumerService,
                searchIndexingService,
                searchResultCacheService,
                searchIndexingFailureService,
                searchMetricsService
        );
    }

    @Test
    void process_routesHotDealStartedToProjectionUpdate() {
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

        processor.process(message, "evt-hot-1", "HOT_DEAL_STARTED");

        verify(searchIndexingService).applyHotDealStarted(101L, 9001L, 9900L);
        verify(searchResultCacheService).evictAll();
    }

    @Test
    void process_routesHotDealEndedToProjectionUpdate() {
        stubIdempotentExecution();

        String message = """
                {
                  "eventId": "evt-hot-2",
                  "eventType": "HOT_DEAL_ENDED",
                  "hotDealId": 9001,
                  "itemId": 101
                }
                """;

        processor.process(message, "evt-hot-2", "HOT_DEAL_ENDED");

        verify(searchIndexingService).applyHotDealEnded(101L, 9001L);
        verify(searchResultCacheService).evictAll();
    }

    @Test
    void process_ignoresInvalidEventWithoutIdempotentExecution() {
        String message = """
                {
                  "eventType": "HOT_DEAL_STARTED",
                  "itemId": 101
                }
                """;

        processor.process(message, null, "HOT_DEAL_STARTED");

        verify(idempotentConsumerService, never()).executeIdempotent(anyString(), anyString(), any());
        verify(searchIndexingService, never()).applyHotDealStarted(any(), any(), any());
        verify(searchIndexingService, never()).applyHotDealEnded(any(), any());
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
