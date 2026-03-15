package com.example.stock.domain;

import com.example.stock.entity.StockItemType;

public record StockTarget(
        StockItemType stockItemType,
        Long referenceId
) {

    public StockTarget {
        if (stockItemType == null) {
            throw new IllegalArgumentException("stockItemType must not be null");
        }
        if (referenceId == null) {
            throw new IllegalArgumentException("referenceId must not be null");
        }
    }

    public static StockTarget of(StockItemType stockItemType, Long referenceId) {
        return new StockTarget(stockItemType, referenceId);
    }
}
