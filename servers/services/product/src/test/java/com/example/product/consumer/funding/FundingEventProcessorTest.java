package com.example.product.consumer.funding;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemStatus;
import com.example.product.entity.item.ItemStatusHistory;
import com.example.product.event.ItemStatusChangedEvent;
import com.example.product.repository.ItemRepository;
import com.example.product.repository.ItemStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundingEventProcessorTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private ItemStatusHistoryRepository statusHistoryRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Mock
    private Item item;

    private FundingEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new FundingEventProcessor(
                itemRepository,
                statusHistoryRepository,
                eventPublisher,
                idempotentConsumerService
        );
    }

    @Test
    void process_fundingSucceeded_changesItemStatus() {
        String message = """
                {"eventId":"evt-1","eventType":"FUNDING_SUCCEEDED","itemId":101}
                """;
        when(idempotentConsumerService.executeIdempotent(eq("evt-1"), eq("FUNDING_EVENT"), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    supplier.get();
                    return Optional.empty();
                });
        when(itemRepository.findById(101L)).thenReturn(Optional.of(item));
        when(item.getStatus()).thenReturn(ItemStatus.FUNDING);

        processor.process(message, "evt-1", "FUNDING_SUCCEEDED");

        verify(item).changeStatus(ItemStatus.FUNDED);
        verify(statusHistoryRepository).save(any(ItemStatusHistory.class));
        verify(eventPublisher).publish(any(ItemStatusChangedEvent.class), any(EventMetadata.class));
    }

    @Test
    void process_invalidEnvelope_skipsProcessing() {
        String message = """
                {"eventType":"FUNDING_SUCCEEDED","itemId":101}
                """;

        processor.process(message, null, "FUNDING_SUCCEEDED");

        verifyNoInteractions(idempotentConsumerService);
        verifyNoInteractions(itemRepository);
        verifyNoInteractions(statusHistoryRepository);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void process_ignoresUnsupportedType() {
        String message = """
                {"eventId":"evt-2","eventType":"FUNDING_UNKNOWN","itemId":101}
                """;

        processor.process(message, "evt-2", "FUNDING_UNKNOWN");

        verifyNoInteractions(itemRepository);
        verifyNoInteractions(statusHistoryRepository);
        verifyNoInteractions(eventPublisher);
        verify(item, never()).changeStatus(any(ItemStatus.class));
    }
}
