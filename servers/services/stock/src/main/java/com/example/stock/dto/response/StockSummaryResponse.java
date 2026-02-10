package com.example.stock.dto.response;

import com.example.stock.entity.StockItem;
import com.example.stock.entity.StockItemType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class StockSummaryResponse {

    private Long itemId;
    private List<StockDetail> stocks;

    @Getter
    @Builder
    public static class StockDetail {
        private Long stockItemId;
        private StockItemType stockItemType;
        private Long referenceId;
        private int totalQuantity;
        private int availableQuantity;
        private int reservedQuantity;

        public static StockDetail from(StockItem stockItem) {
            return StockDetail.builder()
                    .stockItemId(stockItem.getId())
                    .stockItemType(stockItem.getStockItemType())
                    .referenceId(stockItem.getReferenceId())
                    .totalQuantity(stockItem.getTotalQuantity())
                    .availableQuantity(stockItem.getAvailableQuantity())
                    .reservedQuantity(stockItem.getReservedQuantity())
                    .build();
        }
    }

    public static StockSummaryResponse of(Long itemId, List<StockItem> stockItems) {
        return StockSummaryResponse.builder()
                .itemId(itemId)
                .stocks(stockItems.stream().map(StockDetail::from).toList())
                .build();
    }
}
