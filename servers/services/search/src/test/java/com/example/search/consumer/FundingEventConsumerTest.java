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
class FundingEventConsumerTest {

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
    private FundingEventConsumer fundingEventConsumer;

    @Test
    void consume_routesFundingCreatedToProjectionUpdate() {
        stubIdempotentExecution();

        String message = """
                {
                  "eventId": "evt-fund-1",
                  "eventType": "FUNDING_CREATED",
                  "campaignId": 8001,
                  "itemId": 101
                }
                """;

        fundingEventConsumer.consume(message);

        verify(searchIndexingService).applyFundingCreatedByEventTime(101L, 8001L, null);
        verify(searchResultCacheService).evictAll();
    }

    @Test
    void consume_routesFundingSucceededToProjectionUpdate() {
        stubIdempotentExecution();

        String message = """
                {
                  "eventId": "evt-fund-2",
                  "eventType": "FUNDING_SUCCEEDED",
                  "campaignId": 8001,
                  "itemId": 101
                }
                """;

        fundingEventConsumer.consume(message);

        verify(searchIndexingService).applyFundingClosedByEventTime(101L, 8001L, "FUNDED", null);
        verify(searchResultCacheService).evictAll();
    }

    @Test
    void consume_routesFundingFailedToProjectionUpdate() {
        stubIdempotentExecution();

        String message = """
                {
                  "eventId": "evt-fund-3",
                  "eventType": "FUNDING_FAILED",
                  "campaignId": 8001,
                  "itemId": 101
                }
                """;

        fundingEventConsumer.consume(message);

        verify(searchIndexingService).applyFundingClosedByEventTime(101L, 8001L, "FUND_FAILED", null);
        verify(searchResultCacheService).evictAll();
    }

    @Test
    void consume_ignoresInvalidEventWithoutIdempotentExecution() {
        String message = """
                {
                  "eventType": "FUNDING_CREATED",
                  "itemId": 101
                }
                """;

        fundingEventConsumer.consume(message);

        verify(idempotentConsumerService, never()).executeIdempotent(anyString(), anyString(), any());
        verify(searchIndexingService, never()).applyFundingCreatedByEventTime(any(), any(), isNull());
        verify(searchIndexingService, never()).applyFundingClosedByEventTime(any(), any(), any(), isNull());
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
