package com.example.stock.service.query;

import com.example.stock.dto.query.response.StockHistoryQueryResponse;
import com.example.stock.dto.query.response.StockItemQueryResponse;
import com.example.stock.dto.query.response.StockReservationQueryResponse;
import com.example.stock.dto.query.response.StockSummaryQueryResponse;
import com.example.stock.service.query.view.StockHistoryQueryView;
import com.example.stock.service.query.view.StockItemQueryView;
import com.example.stock.service.query.view.StockReservationQueryView;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StockQueryAssembler {

    public StockItemQueryResponse toStockResponse(StockItemQueryView view) {
        return StockItemQueryResponse.builder()
                .id(view.id())
                .itemId(view.itemId())
                .stockItemType(view.target().stockItemType())
                .referenceId(view.target().referenceId())
                .totalQuantity(view.totalQuantity())
                .availableQuantity(view.availableQuantity())
                .reservedQuantity(view.reservedQuantity())
                .build();
    }

    public StockSummaryQueryResponse toStockSummaryResponse(Long itemId, List<StockItemQueryView> views) {
        return StockSummaryQueryResponse.builder()
                .itemId(itemId)
                .stocks(views.stream()
                        .map(this::toStockDetail)
                        .toList())
                .build();
    }

    public StockHistoryQueryResponse toStockHistoryResponse(StockHistoryQueryView view) {
        return StockHistoryQueryResponse.builder()
                .id(view.id())
                .stockItemId(view.stockItemId())
                .changeType(view.changeType())
                .quantity(view.quantity())
                .reason(view.reason())
                .reservationId(view.reservationId())
                .createdAt(view.createdAt())
                .build();
    }

    public StockReservationQueryResponse toReservationResponse(StockReservationQueryView view) {
        return StockReservationQueryResponse.builder()
                .stockItemId(view.stockItemId())
                .userId(view.userId())
                .orderId(view.orderId())
                .quantity(view.quantity())
                .status(view.status())
                .expiredAt(view.expiredAt())
                .build();
    }

    private StockSummaryQueryResponse.StockDetail toStockDetail(StockItemQueryView view) {
        return StockSummaryQueryResponse.StockDetail.builder()
                .stockItemId(view.id())
                .stockItemType(view.target().stockItemType())
                .referenceId(view.target().referenceId())
                .totalQuantity(view.totalQuantity())
                .availableQuantity(view.availableQuantity())
                .reservedQuantity(view.reservedQuantity())
                .build();
    }
}
