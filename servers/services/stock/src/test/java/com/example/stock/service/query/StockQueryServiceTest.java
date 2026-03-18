package com.example.stock.service.query;

import com.example.core.exception.BusinessException;
import com.example.stock.entity.ChangeType;
import com.example.stock.entity.ReservationStatus;
import com.example.stock.entity.StockItemType;
import com.example.stock.exception.StockErrorCode;
import com.example.stock.repository.StockHistoryRepository;
import com.example.stock.repository.StockItemRepository;
import com.example.stock.repository.StockReservationRepository;
import com.example.stock.service.query.view.StockHistoryQueryView;
import com.example.stock.service.query.view.StockItemQueryView;
import com.example.stock.service.query.view.StockReservationQueryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockQueryServiceTest {

    @Mock
    private StockItemRepository stockItemRepository;

    @Mock
    private StockHistoryRepository stockHistoryRepository;

    @Mock
    private StockReservationRepository stockReservationRepository;

    private StockQueryService stockQueryService;

    @BeforeEach
    void setUp() {
        StockQueryReader stockQueryReader = new StockQueryReader(
                stockItemRepository,
                stockHistoryRepository,
                stockReservationRepository
        );
        stockQueryService = new StockQueryService(stockQueryReader, new StockQueryAssembler());
    }

    @Test
    void getStock_returnsMappedResponse() {
        when(stockItemRepository.findViewById(10L)).thenReturn(Optional.of(
                new StockItemQueryView(10L, 100L, StockItemType.ITEM_OPTION, 501L, 30, 24, 6)
        ));

        var response = stockQueryService.getStock(10L);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getItemId()).isEqualTo(100L);
        assertThat(response.getStockItemType()).isEqualTo(StockItemType.ITEM_OPTION);
        assertThat(response.getReferenceId()).isEqualTo(501L);
        assertThat(response.getAvailableQuantity()).isEqualTo(24);
    }

    @Test
    void getStocksByItemId_returnsSummaryResponseBuiltFromViews() {
        when(stockItemRepository.findViewsByItemId(100L)).thenReturn(List.of(
                new StockItemQueryView(10L, 100L, StockItemType.ITEM_OPTION, 501L, 30, 24, 6),
                new StockItemQueryView(11L, 100L, StockItemType.SEAT_GRADE, 601L, 12, 10, 2)
        ));

        var response = stockQueryService.getStocksByItemId(100L);

        assertThat(response.getItemId()).isEqualTo(100L);
        assertThat(response.getAvailableQuantity()).isEqualTo(34);
        assertThat(response.getSoldQuantity()).isEqualTo(0);
        assertThat(response.isSoldOut()).isFalse();
        assertThat(response.getOptionStocks()).hasSize(1);
        assertThat(response.getOptionStocks().getFirst().getItemOptionId()).isEqualTo(501L);
        assertThat(response.getStocks()).hasSize(2);
        assertThat(response.getStocks().getFirst().getStockItemType()).isEqualTo(StockItemType.ITEM_OPTION);
        assertThat(response.getStocks().getFirst().getReferenceId()).isEqualTo(501L);
        assertThat(response.getStocks().get(1).getStockItemType()).isEqualTo(StockItemType.SEAT_GRADE);
    }

    @Test
    void getStockHistory_checksExistenceAndMapsHistoryViews() {
        LocalDateTime now = LocalDateTime.now();
        when(stockItemRepository.existsById(10L)).thenReturn(true);
        when(stockHistoryRepository.findViewsByStockItemIdOrderByCreatedAtDesc(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        new StockHistoryQueryView(1L, 10L, ChangeType.RESERVE, 3, "reserve", 99L, now)
                )));

        var responses = stockQueryService.getStockHistory(10L, 0, 20);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getChangeType()).isEqualTo(ChangeType.RESERVE);
        assertThat(responses.getFirst().getReservationId()).isEqualTo(99L);
        verify(stockItemRepository).existsById(10L);
    }

    @Test
    void getStockHistory_throwsWhenStockDoesNotExist() {
        when(stockItemRepository.existsById(10L)).thenReturn(false);

        assertThatThrownBy(() -> stockQueryService.getStockHistory(10L, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(StockErrorCode.STOCK_NOT_FOUND);
    }

    @Test
    void getReservationsByOrderId_mapsReservationViews() {
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);
        when(stockReservationRepository.findViewsByOrderId(55L)).thenReturn(List.of(
                new StockReservationQueryView(10L, 77L, 55L, 2, ReservationStatus.RESERVED, expiresAt)
        ));

        var responses = stockQueryService.getReservationsByOrderId(55L);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getStockItemId()).isEqualTo(10L);
        assertThat(responses.getFirst().getOrderId()).isEqualTo(55L);
        assertThat(responses.getFirst().getStatus()).isEqualTo(ReservationStatus.RESERVED);
    }
}
