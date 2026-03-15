package com.example.stock.service.query.view;

import com.example.stock.domain.StockTarget;
import com.example.stock.entity.StockItemType;

public record StockItemQueryView(
        Long id,
        Long itemId,
        StockTarget target,
        int totalQuantity,
        int availableQuantity,
        int reservedQuantity
) {

    public StockItemQueryView(
            Long id,
            Long itemId,
            StockItemType stockItemType,
            Long referenceId,
            int totalQuantity,
            int availableQuantity,
            int reservedQuantity
    ) {
        this(id, itemId, StockTarget.of(stockItemType, referenceId), totalQuantity, availableQuantity, reservedQuantity);
    }
}
