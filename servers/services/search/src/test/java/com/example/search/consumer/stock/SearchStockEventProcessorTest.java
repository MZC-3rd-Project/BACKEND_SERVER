package com.example.search.consumer.stock;

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
class SearchStockEventProcessorTest {

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

    private SearchStockEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new SearchStockEventProcessor(
                idempotentConsumerService,
                searchIndexingService,
                searchResultCacheService,
                searchIndexingFailureService,
                searchMetricsService
        );
    }

    @Test
    void process_routesStockDecreasedToIndexingService() {
        stubIdempotentExecution();

        String message = """
                {
                  "eventId": "evt-stock-1",
                  "eventType": "STOCK_DECREASED",
                  "stockItemId": 55,
                  "itemId": 101,
                  "quantity": 2,
                  "remainingQuantity": 8
                }
                """;

        processor.process(message, "evt-stock-1", "STOCK_DECREASED");

        verify(searchIndexingService).updateItemStock(101L, 8);
        verify(searchResultCacheService).evictAll();
    }

    @Test
    void process_routesItemAvailableStockChangedToVersionedUpdate() {
        stubIdempotentExecution();

        String message = """
                {
                  "eventId": "evt-stock-2",
                  "eventType": "ITEM_AVAILABLE_STOCK_CHANGED",
                  "itemId": 101,
                  "availableStockTotal": 11,
                  "stockVersion": 57
                }
                """;

        processor.process(message, "evt-stock-2", "ITEM_AVAILABLE_STOCK_CHANGED");

        verify(searchIndexingService).updateItemStockVersioned(101L, 11, 57L);
        verify(searchResultCacheService).evictAll();
    }

    @Test
    void process_skipsVersionedUpdateWhenSnapshotFieldsMissing() {
        String message = """
                {
                  "eventId": "evt-stock-3",
                  "eventType": "ITEM_AVAILABLE_STOCK_CHANGED",
                  "itemId": 101,
                  "availableStockTotal": 11
                }
                """;

        processor.process(message, "evt-stock-3", "ITEM_AVAILABLE_STOCK_CHANGED");

        verify(searchIndexingService, never()).updateItemStockVersioned(any(), any(), any());
    }

    @Test
    void process_ignoresInvalidEventWithoutIdempotentExecution() {
        String message = """
                {
                  "eventType": "STOCK_DECREASED",
                  "itemId": 101
                }
                """;

        processor.process(message, null, "STOCK_DECREASED");

        verify(idempotentConsumerService, never()).executeIdempotent(anyString(), anyString(), any());
        verify(searchIndexingService, never()).updateItemStock(any(), any());
        verify(searchIndexingService, never()).updateItemStockVersioned(any(), any(), any());
        verify(searchResultCacheService, never()).evictAll();
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
