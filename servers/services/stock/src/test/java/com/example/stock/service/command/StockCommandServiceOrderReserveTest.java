package com.example.stock.service.command;

import com.example.core.id.Snowflake;
import com.example.event.EventPublisher;
import com.example.stock.dto.request.ReserveOrderStockRequest;
import com.example.stock.dto.response.ReserveOrderStockResponse;
import com.example.stock.entity.OrderReserveIdempotency;
import com.example.stock.entity.ReservationStatus;
import com.example.stock.entity.StockItem;
import com.example.stock.entity.StockItemType;
import com.example.stock.entity.StockReservation;
import com.example.stock.entity.StockSyncVersion;
import com.example.stock.exception.StockErrorCode;
import com.example.stock.repository.OrderReserveIdempotencyRepository;
import com.example.stock.repository.StockHistoryRepository;
import com.example.stock.repository.StockItemRepository;
import com.example.stock.repository.StockReservationRepository;
import com.example.stock.repository.StockSyncVersionRepository;
import com.example.stock.service.StockCacheService;
import com.example.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockCommandServiceOrderReserveTest {

    @Mock
    private StockItemRepository stockItemRepository;

    @Mock
    private StockReservationRepository stockReservationRepository;

    @Mock
    private OrderReserveIdempotencyRepository orderReserveIdempotencyRepository;

    @Mock
    private StockHistoryRepository stockHistoryRepository;

    @Mock
    private StockSyncVersionRepository stockSyncVersionRepository;

    @Mock
    private StockCacheService stockCacheService;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private Snowflake snowflake;

    @InjectMocks
    private StockCommandService stockCommandService;

    @Test
    void reserveOrderStock_issuesOrderIdAndReturnsReservedItems() {
        StockItem stockItem = StockItem.create(10L, StockItemType.ITEM_OPTION, 101L, 20);
        StockSyncVersion stockSyncVersion = StockSyncVersion.initialize(10L);
        ReflectionTestUtils.setField(stockItem, "id", 1000L);

        when(snowflake.nextId()).thenReturn(9999L);
        when(stockItemRepository.findByItemIdAndStockItemTypeAndReferenceIdWithLock(10L, StockItemType.ITEM_OPTION, 101L))
                .thenReturn(Optional.of(stockItem));
        when(orderReserveIdempotencyRepository.findByUserIdAndIdempotencyKey(55L, "idem-1"))
                .thenReturn(Optional.empty());
        when(stockItemRepository.sumAvailableQuantityByItemId(10L)).thenReturn(18L);
        when(stockSyncVersionRepository.findByItemIdWithLock(10L)).thenReturn(Optional.of(stockSyncVersion));

        ReserveOrderStockResponse response = stockCommandService.reserveOrderStock(request());

        assertThat(response.getOrderId()).isEqualTo(9999L);
        assertThat(response.getReservedItems()).hasSize(1);
        assertThat(response.getReservedItems().getFirst().getItemId()).isEqualTo(10L);
        assertThat(response.getReservedItems().getFirst().getStockItemType()).isEqualTo("ITEM_OPTION");
        assertThat(response.getReservedItems().getFirst().getReferenceId()).isEqualTo(101L);
        assertThat(response.getReservedItems().getFirst().getQuantity()).isEqualTo(2);
        assertThat(stockItem.getAvailableQuantity()).isEqualTo(18);
        verify(stockReservationRepository).save(any());
        verify(orderReserveIdempotencyRepository).save(any(OrderReserveIdempotency.class));
    }

    @Test
    void reserveOrderStock_returnsExistingOrderForActiveIdempotentRequest() {
        OrderReserveIdempotency idempotency = OrderReserveIdempotency.create(
                55L,
                "idem-1",
                "NORMAL##10:ITEM_OPTION:101:2",
                9999L,
                LocalDateTime.now().plusMinutes(5)
        );
        StockReservation reservation = StockReservation.create(1000L, 55L, 9999L, 2, LocalDateTime.now().plusMinutes(5));

        when(orderReserveIdempotencyRepository.findByUserIdAndIdempotencyKey(55L, "idem-1"))
                .thenReturn(Optional.of(idempotency));
        when(stockReservationRepository.findByOrderId(9999L)).thenReturn(List.of(reservation));

        ReserveOrderStockResponse response = stockCommandService.reserveOrderStock(request());

        assertThat(response.getOrderId()).isEqualTo(9999L);
        assertThat(response.getReservedItems()).hasSize(1);
        verify(stockItemRepository, never()).findByItemIdAndStockItemTypeAndReferenceIdWithLock(any(), any(), any());
        verify(stockReservationRepository, never()).save(any());
    }

    @Test
    void reserveOrderStock_throwsWhenSameIdempotencyKeyHasDifferentPayload() {
        OrderReserveIdempotency idempotency = OrderReserveIdempotency.create(
                55L,
                "idem-1",
                "NORMAL##10:ITEM_OPTION:101:1",
                9999L,
                LocalDateTime.now().plusMinutes(5)
        );

        when(orderReserveIdempotencyRepository.findByUserIdAndIdempotencyKey(55L, "idem-1"))
                .thenReturn(Optional.of(idempotency));

        assertThatThrownBy(() -> stockCommandService.reserveOrderStock(request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(StockErrorCode.ORDER_RESERVE_IDEMPOTENCY_CONFLICT);
    }

    private ReserveOrderStockRequest request() {
        ReserveOrderStockRequest.LineItem lineItem = new ReserveOrderStockRequest.LineItem();
        ReflectionTestUtils.setField(lineItem, "itemId", 10L);
        ReflectionTestUtils.setField(lineItem, "stockItemType", StockItemType.ITEM_OPTION);
        ReflectionTestUtils.setField(lineItem, "referenceId", 101L);
        ReflectionTestUtils.setField(lineItem, "quantity", 2);

        ReserveOrderStockRequest request = new ReserveOrderStockRequest();
        ReflectionTestUtils.setField(request, "channelType", "NORMAL");
        ReflectionTestUtils.setField(request, "userId", 55L);
        ReflectionTestUtils.setField(request, "idempotencyKey", "idem-1");
        ReflectionTestUtils.setField(request, "lineItems", List.of(lineItem));
        return request;
    }
}
