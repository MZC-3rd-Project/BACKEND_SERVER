package com.example.stock.service.command;

import com.example.event.DomainEvent;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.stock.dto.request.StockDecreaseRequest;
import com.example.stock.dto.request.StockIncreaseRequest;
import com.example.stock.entity.StockItem;
import com.example.stock.entity.StockItemType;
import com.example.stock.entity.StockSyncVersion;
import com.example.stock.event.ItemAvailableStockChangedEvent;
import com.example.stock.repository.StockHistoryRepository;
import com.example.stock.repository.StockItemRepository;
import com.example.stock.repository.StockReservationRepository;
import com.example.stock.repository.StockSyncVersionRepository;
import com.example.stock.service.StockCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockCommandServiceStockSnapshotEventTest {

    @Mock
    private StockItemRepository stockItemRepository;

    @Mock
    private StockReservationRepository stockReservationRepository;

    @Mock
    private StockHistoryRepository stockHistoryRepository;

    @Mock
    private StockSyncVersionRepository stockSyncVersionRepository;

    @Mock
    private StockCacheService stockCacheService;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private StockCommandService stockCommandService;

    @Test
    void decreaseStock_publishesItemSnapshotEventWithVersionAndItemKey() {
        StockItem stockItem = StockItem.create(200L, StockItemType.SEAT_GRADE, 300L, 10);
        ReflectionTestUtils.setField(stockItem, "id", 100L);

        StockDecreaseRequest request = new StockDecreaseRequest();
        ReflectionTestUtils.setField(request, "stockItemId", 100L);
        ReflectionTestUtils.setField(request, "quantity", 1);
        ReflectionTestUtils.setField(request, "reason", "purchase");

        StockSyncVersion syncVersion = StockSyncVersion.initialize(200L);
        ReflectionTestUtils.setField(syncVersion, "version", 4L);

        when(stockItemRepository.findByIdWithLock(100L)).thenReturn(Optional.of(stockItem));
        when(stockItemRepository.sumAvailableQuantityByItemId(200L)).thenReturn(9L);
        when(stockSyncVersionRepository.findByItemIdWithLock(200L)).thenReturn(Optional.of(syncVersion));

        stockCommandService.decreaseStock(request);

        SnapshotEventResult snapshot = captureSnapshotEvent();
        assertThat(snapshot.event.getItemId()).isEqualTo(200L);
        assertThat(snapshot.event.getAvailableStockTotal()).isEqualTo(9);
        assertThat(snapshot.event.getStockVersion()).isEqualTo(5L);
        assertThat(snapshot.metadata.aggregateId()).isEqualTo("200");
    }

    @Test
    void increaseStock_publishesItemSnapshotEvent() {
        StockItem stockItem = StockItem.create(201L, StockItemType.SEAT_GRADE, 301L, 10);
        ReflectionTestUtils.setField(stockItem, "id", 101L);

        StockIncreaseRequest request = new StockIncreaseRequest();
        ReflectionTestUtils.setField(request, "stockItemId", 101L);
        ReflectionTestUtils.setField(request, "quantity", 2);
        ReflectionTestUtils.setField(request, "reason", "refund");

        StockSyncVersion syncVersion = StockSyncVersion.initialize(201L);
        ReflectionTestUtils.setField(syncVersion, "version", 9L);

        when(stockItemRepository.findByIdWithLock(101L)).thenReturn(Optional.of(stockItem));
        when(stockItemRepository.sumAvailableQuantityByItemId(201L)).thenReturn(10L);
        when(stockSyncVersionRepository.findByItemIdWithLock(201L)).thenReturn(Optional.of(syncVersion));

        stockCommandService.increaseStock(request);

        SnapshotEventResult snapshot = captureSnapshotEvent();
        assertThat(snapshot.event.getItemId()).isEqualTo(201L);
        assertThat(snapshot.event.getAvailableStockTotal()).isEqualTo(10);
        assertThat(snapshot.event.getStockVersion()).isEqualTo(10L);
        assertThat(snapshot.metadata.aggregateId()).isEqualTo("201");
    }

    private SnapshotEventResult captureSnapshotEvent() {
        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        ArgumentCaptor<EventMetadata> metadataCaptor = ArgumentCaptor.forClass(EventMetadata.class);
        verify(eventPublisher, atLeastOnce()).publish(eventCaptor.capture(), metadataCaptor.capture());

        List<DomainEvent> events = eventCaptor.getAllValues();
        int snapshotIndex = IntStream.range(0, events.size())
                .filter(index -> events.get(index) instanceof ItemAvailableStockChangedEvent)
                .findFirst()
                .orElseThrow();

        ItemAvailableStockChangedEvent event = (ItemAvailableStockChangedEvent) events.get(snapshotIndex);
        EventMetadata metadata = metadataCaptor.getAllValues().get(snapshotIndex);
        return new SnapshotEventResult(event, metadata);
    }

    private record SnapshotEventResult(ItemAvailableStockChangedEvent event, EventMetadata metadata) {
    }
}
