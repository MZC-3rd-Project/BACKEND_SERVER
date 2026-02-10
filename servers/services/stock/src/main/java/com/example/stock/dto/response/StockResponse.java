package com.example.stock.dto.response;

import com.example.stock.entity.StockItem;
import com.example.stock.entity.StockItemType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StockResponse {

    private Long id;
    private Long itemId;
    private StockItemType stockItemType;
    private Long referenceId;
    private int totalQuantity;
    private int availableQuantity;
    private int reservedQuantity;

    public static StockResponse from(StockItem stockItem) {
        return StockResponse.builder()
                .id(stockItem.getId())
                .itemId(stockItem.getItemId())
                .stockItemType(stockItem.getStockItemType())
                .referenceId(stockItem.getReferenceId())
                .totalQuantity(stockItem.getTotalQuantity())
                .availableQuantity(stockItem.getAvailableQuantity())
                .reservedQuantity(stockItem.getReservedQuantity())
                .build();
    }
}
