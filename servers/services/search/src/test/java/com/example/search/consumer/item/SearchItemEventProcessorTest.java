package com.example.search.consumer.item;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.search.service.index.SearchIndexingFailureService;
import com.example.search.service.index.SearchIndexingService;
import com.example.search.service.metrics.SearchMetricsService;
import com.example.search.service.query.cache.SearchResultCacheService;
import com.example.search.service.thumbnail.SearchThumbnailEnrichmentTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchItemEventProcessorTest {

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

    @Mock
    private SearchThumbnailEnrichmentTaskService thumbnailEnrichmentTaskService;

    private SearchItemEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new SearchItemEventProcessor(
                idempotentConsumerService,
                searchIndexingService,
                searchResultCacheService,
                searchIndexingFailureService,
                searchMetricsService,
                thumbnailEnrichmentTaskService
        );
    }

    @Test
    void process_routesItemCreatedToIndexingService() {
        stubIdempotentExecution();

        String message = """
                {
                  "eventId": "evt-item-1",
                  "eventType": "ITEM_CREATED",
                  "itemId": 101,
                  "title": "아이폰 케이스",
                  "itemType": "GOODS",
                  "price": 25000,
                  "stockItems": [
                    {"type": "GOODS", "referenceId": 1, "totalQuantity": 3},
                    {"type": "GOODS", "referenceId": 2, "totalQuantity": 7}
                  ]
                }
                """;

        processor.process(message, "evt-item-1", "ITEM_CREATED");

        verify(searchIndexingService).indexItem(
                eq(101L),
                eq("아이폰 케이스"),
                eq("GOODS"),
                eq("GOODS"),
                eq(25000L),
                eq("DRAFT"),
                eq(10),
                isNull(),
                anyLong()
        );
        verify(searchResultCacheService).evictAll();
    }

    @Test
    void process_routesItemUpdatedToIndexingService() {
        stubIdempotentExecution();

        String message = """
                {
                  "eventId": "evt-item-2",
                  "eventType": "ITEM_UPDATED",
                  "itemId": 101,
                  "title": "아이폰 케이스 2",
                  "price": 25000
                }
                """;

        processor.process(message, "evt-item-2", "ITEM_UPDATED");

        verify(searchIndexingService).updateItem(
                eq(101L),
                eq("아이폰 케이스 2"),
                eq(25000L),
                isNull(),
                anyLong()
        );
        verify(searchResultCacheService).evictAll();
    }

    @Test
    void process_routesItemStatusChangedToIndexingService() {
        stubIdempotentExecution();

        String message = """
                {
                  "eventId": "evt-item-3",
                  "eventType": "ITEM_STATUS_CHANGED",
                  "itemId": 101,
                  "previousStatus": "READY",
                  "newStatus": "SELLING"
                }
                """;

        processor.process(message, "evt-item-3", "ITEM_STATUS_CHANGED");

        verify(searchIndexingService).updateItemStatus(101L, "SELLING");
        verify(searchResultCacheService).evictAll();
    }

    @Test
    void process_routesItemDeletedToIndexingService() {
        stubIdempotentExecution();

        String message = """
                {
                  "eventId": "evt-item-4",
                  "eventType": "ITEM_DELETED",
                  "itemId": 101
                }
                """;

        processor.process(message, "evt-item-4", "ITEM_DELETED");

        verify(searchIndexingService).deleteItem(101L);
        verify(thumbnailEnrichmentTaskService).removeTask(101L);
        verify(searchResultCacheService).evictAll();
    }

    @Test
    void process_ignoresInvalidEventWithoutIdempotentExecution() {
        String message = """
                {
                  "eventType": "ITEM_CREATED",
                  "itemId": 101
                }
                """;

        processor.process(message, null, "ITEM_CREATED");

        verify(idempotentConsumerService, never()).executeIdempotent(anyString(), anyString(), any());
        verify(searchIndexingService, never()).indexItem(any(), any(), any(), any(), any(), any(), any(), any(), any());
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
