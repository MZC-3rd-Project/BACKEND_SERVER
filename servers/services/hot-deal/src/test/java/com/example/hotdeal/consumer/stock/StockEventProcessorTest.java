package com.example.hotdeal.consumer.stock;

import com.example.clients.product.facade.ProductItemQueryClientFacade;
import com.example.config.kafka.IdempotentConsumerService;
import com.example.hotdeal.entity.HotDealStatus;
import com.example.hotdeal.repository.HotDealRepository;
import com.example.hotdeal.service.HotDealCommandService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockEventProcessorTest {

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Mock
    private HotDealRepository hotDealRepository;

    @Mock
    private HotDealCommandService hotDealCommandService;

    @Mock
    private ProductItemQueryClientFacade productClient;

    private StockEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new StockEventProcessor(
                idempotentConsumerService,
                hotDealRepository,
                hotDealCommandService,
                productClient
        );
    }

    @Test
    void process_createsHotDealWhenThresholdReached() throws Exception {
        String message = """
                {
                  "eventId": "evt-stock-1",
                  "eventType": "STOCK_THRESHOLD_REACHED",
                  "itemId": 101,
                  "totalQuantity": 100,
                  "remainingQuantity": 50
                }
                """;
        stubIdempotent("evt-stock-1");
        when(hotDealRepository.existsByItemIdAndStatusIn(eq(101L), any()))
                .thenReturn(false);
        when(productClient.findItem(101L))
                .thenReturn(new ObjectMapper().readTree("""
                        {"title":"테스트 상품","price":15000}
                        """));

        processor.process(message, "evt-stock-1", "STOCK_THRESHOLD_REACHED");

        verify(hotDealRepository).existsByItemIdAndStatusIn(101L, List.of(HotDealStatus.SCHEDULED, HotDealStatus.ACTIVE));
        verify(hotDealCommandService).createAndActivate(
                eq(101L),
                eq("테스트 상품"),
                eq(15000L),
                eq(10),
                eq(50),
                eq(1),
                isA(java.time.LocalDateTime.class),
                isA(java.time.LocalDateTime.class),
                contains("재고 임계값 이벤트")
        );
    }

    @Test
    void process_skipsInvalidPayload() {
        String message = """
                {
                  "eventId": "evt-stock-2",
                  "eventType": "STOCK_THRESHOLD_REACHED",
                  "totalQuantity": 100,
                  "remainingQuantity": 50
                }
                """;

        processor.process(message, "evt-stock-2", "STOCK_THRESHOLD_REACHED");

        verifyNoInteractions(idempotentConsumerService);
        verifyNoInteractions(hotDealRepository);
        verifyNoInteractions(hotDealCommandService);
        verifyNoInteractions(productClient);
    }

    @Test
    void process_skipsWhenActiveDealExists() {
        String message = """
                {
                  "eventId": "evt-stock-3",
                  "eventType": "STOCK_THRESHOLD_REACHED",
                  "itemId": 101,
                  "totalQuantity": 100,
                  "remainingQuantity": 50
                }
                """;
        stubIdempotent("evt-stock-3");
        when(hotDealRepository.existsByItemIdAndStatusIn(eq(101L), any()))
                .thenReturn(true);

        processor.process(message, "evt-stock-3", "STOCK_THRESHOLD_REACHED");

        verify(hotDealRepository).existsByItemIdAndStatusIn(eq(101L), any());
        verifyNoInteractions(hotDealCommandService);
        verifyNoInteractions(productClient);
    }

    private void stubIdempotent(String eventId) {
        when(idempotentConsumerService.executeIdempotent(eq(eventId), eq("STOCK_EVENT"), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    supplier.get();
                    return Optional.empty();
                });
    }
}
