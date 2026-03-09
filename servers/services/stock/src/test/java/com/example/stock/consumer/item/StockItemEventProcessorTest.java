package com.example.stock.consumer.item;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.stock.dto.request.InitializeStockRequest;
import com.example.stock.service.command.StockCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockItemEventProcessorTest {

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Mock
    private StockCommandService stockCommandService;

    private StockItemEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new StockItemEventProcessor(idempotentConsumerService, stockCommandService);
    }

    @Test
    void process_itemCreated_initializesStock() {
        String message = """
                {
                  "eventId": "evt-1",
                  "eventType": "ITEM_CREATED",
                  "itemId": 101,
                  "stockItems": [
                    {"type": "ITEM_OPTION", "referenceId": 201, "totalQuantity": 7}
                  ]
                }
                """;
        when(idempotentConsumerService.executeIdempotent(eq("evt-1"), eq("ITEM_EVENT"), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    supplier.get();
                    return Optional.empty();
                });

        processor.process(message, "evt-1", "ITEM_CREATED");

        ArgumentCaptor<InitializeStockRequest> captor = ArgumentCaptor.forClass(InitializeStockRequest.class);
        verify(stockCommandService).initializeStock(captor.capture());
        InitializeStockRequest request = captor.getValue();
        assertThat(request.getItemId()).isEqualTo(101L);
        assertThat(request.getReferenceId()).isEqualTo(201L);
        assertThat(request.getTotalQuantity()).isEqualTo(7);
        assertThat(request.getStockItemType().name()).isEqualTo("ITEM_OPTION");
    }

    @Test
    void process_invalidEnvelope_skipsProcessing() {
        String message = """
                {"eventType":"ITEM_CREATED","itemId":101}
                """;

        processor.process(message, null, "ITEM_CREATED");

        verifyNoInteractions(idempotentConsumerService);
        verifyNoInteractions(stockCommandService);
    }

    @Test
    void process_ignoresUnsupportedType() {
        String message = """
                {"eventId":"evt-2","eventType":"ITEM_UPDATED","itemId":101}
                """;

        processor.process(message, "evt-2", "ITEM_UPDATED");

        verify(stockCommandService, never()).initializeStock(any());
    }
}
