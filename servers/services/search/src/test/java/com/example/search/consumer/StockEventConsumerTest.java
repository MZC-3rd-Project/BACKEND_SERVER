package com.example.search.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.search.service.index.SearchIndexingService;
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
class StockEventConsumerTest {

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Mock
    private SearchIndexingService searchIndexingService;

    @InjectMocks
    private StockEventConsumer stockEventConsumer;

    @Test
    void consume_routesStockDecreasedToIndexingService() {
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

        stockEventConsumer.consume(message);

        verify(searchIndexingService).updateItemStock(101L, 8);
    }

    @Test
    void consume_ignoresInvalidEventWithoutIdempotentExecution() {
        String message = """
                {
                  "eventType": "STOCK_DECREASED",
                  "itemId": 101
                }
                """;

        stockEventConsumer.consume(message);

        verify(idempotentConsumerService, never()).executeIdempotent(anyString(), anyString(), any());
        verify(searchIndexingService, never()).updateItemStock(any(), any());
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
