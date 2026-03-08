package com.example.search.consumer.funding;

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
class SearchFundingEventProcessorTest {

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

    private SearchFundingEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new SearchFundingEventProcessor(
                idempotentConsumerService,
                searchIndexingService,
                searchResultCacheService,
                searchIndexingFailureService,
                searchMetricsService
        );
    }

    @Test
    void process_routesFundingCreatedToProjectionUpdate() {
        stubIdempotentExecution();

        String message = """
                {
                  "eventId": "evt-fund-1",
                  "eventType": "FUNDING_CREATED",
                  "campaignId": 8001,
                  "itemId": 101
                }
                """;

        processor.process(message, "evt-fund-1", "FUNDING_CREATED");

        verify(searchIndexingService).applyFundingCreated(101L, 8001L);
        verify(searchResultCacheService).evictAll();
    }

    @Test
    void process_routesFundingSucceededToProjectionUpdate() {
        stubIdempotentExecution();

        String message = """
                {
                  "eventId": "evt-fund-2",
                  "eventType": "FUNDING_SUCCEEDED",
                  "campaignId": 8001,
                  "itemId": 101
                }
                """;

        processor.process(message, "evt-fund-2", "FUNDING_SUCCEEDED");

        verify(searchIndexingService).applyFundingClosed(101L, 8001L, "FUNDED");
        verify(searchResultCacheService).evictAll();
    }

    @Test
    void process_routesFundingFailedToProjectionUpdate() {
        stubIdempotentExecution();

        String message = """
                {
                  "eventId": "evt-fund-3",
                  "eventType": "FUNDING_FAILED",
                  "campaignId": 8001,
                  "itemId": 101
                }
                """;

        processor.process(message, "evt-fund-3", "FUNDING_FAILED");

        verify(searchIndexingService).applyFundingClosed(101L, 8001L, "FUND_FAILED");
        verify(searchResultCacheService).evictAll();
    }

    @Test
    void process_ignoresInvalidEventWithoutIdempotentExecution() {
        String message = """
                {
                  "eventType": "FUNDING_CREATED",
                  "itemId": 101
                }
                """;

        processor.process(message, null, "FUNDING_CREATED");

        verify(idempotentConsumerService, never()).executeIdempotent(anyString(), anyString(), any());
        verify(searchIndexingService, never()).applyFundingCreated(any(), any());
        verify(searchIndexingService, never()).applyFundingClosed(any(), any(), any());
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
