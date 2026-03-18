package com.example.stock.service.query;

import com.example.stock.entity.ChangeType;
import com.example.stock.entity.ReservationStatus;
import com.example.stock.entity.StockItemType;
import com.example.stock.service.query.view.StockHistoryQueryView;
import com.example.stock.service.query.view.StockItemQueryView;
import com.example.stock.service.query.view.StockReservationQueryView;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StockQueryAssemblerTest {

    private final StockQueryAssembler stockQueryAssembler = new StockQueryAssembler();

    @Test
    void toStockResponse_exposesStockTargetFields() {
        StockItemQueryView view = new StockItemQueryView(10L, 100L, StockItemType.ITEM_OPTION, 501L, 30, 24, 6);

        var response = stockQueryAssembler.toStockResponse(view);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getItemId()).isEqualTo(100L);
        assertThat(response.getStockItemType()).isEqualTo(StockItemType.ITEM_OPTION);
        assertThat(response.getReferenceId()).isEqualTo(501L);
    }

    @Test
    void toStockSummaryResponse_preservesEachTarget() {
        List<StockItemQueryView> views = List.of(
                new StockItemQueryView(10L, 100L, StockItemType.ITEM_OPTION, 501L, 30, 24, 6),
                new StockItemQueryView(11L, 100L, StockItemType.SEAT_GRADE, 601L, 12, 10, 2)
        );

        var response = stockQueryAssembler.toStockSummaryResponse(100L, views);

        assertThat(response.getItemId()).isEqualTo(100L);
        assertThat(response.getTotalQuantity()).isEqualTo(42);
        assertThat(response.getAvailableQuantity()).isEqualTo(34);
        assertThat(response.getReservedQuantity()).isEqualTo(8);
        assertThat(response.getSoldQuantity()).isEqualTo(0);
        assertThat(response.isSoldOut()).isFalse();
        assertThat(response.getOptionStocks()).hasSize(1);
        assertThat(response.getOptionStocks().getFirst().getItemOptionId()).isEqualTo(501L);
        assertThat(response.getOptionStocks().getFirst().getAvailableQuantity()).isEqualTo(24);
        assertThat(response.getStocks()).hasSize(2);
        assertThat(response.getStocks().getFirst().getStockItemType()).isEqualTo(StockItemType.ITEM_OPTION);
        assertThat(response.getStocks().getFirst().getReferenceId()).isEqualTo(501L);
        assertThat(response.getStocks().get(1).getStockItemType()).isEqualTo(StockItemType.SEAT_GRADE);
        assertThat(response.getStocks().get(1).getReferenceId()).isEqualTo(601L);
    }

    @Test
    void toStockHistoryResponse_preservesHistoryFields() {
        LocalDateTime createdAt = LocalDateTime.now();
        StockHistoryQueryView view = new StockHistoryQueryView(1L, 10L, ChangeType.RESERVE, 3, "reserve", 99L, createdAt);

        var response = stockQueryAssembler.toStockHistoryResponse(view);

        assertThat(response.getStockItemId()).isEqualTo(10L);
        assertThat(response.getChangeType()).isEqualTo(ChangeType.RESERVE);
        assertThat(response.getReservationId()).isEqualTo(99L);
        assertThat(response.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void toReservationResponse_exposesReservationStatus() {
        LocalDateTime expiredAt = LocalDateTime.now().plusMinutes(10);
        StockReservationQueryView view = new StockReservationQueryView(
                10L,
                77L,
                55L,
                2,
                ReservationStatus.RESERVED,
                expiredAt
        );

        var response = stockQueryAssembler.toReservationResponse(view);

        assertThat(response.getStockItemId()).isEqualTo(10L);
        assertThat(response.getOrderId()).isEqualTo(55L);
        assertThat(response.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(response.getExpiredAt()).isEqualTo(expiredAt);
    }
}
