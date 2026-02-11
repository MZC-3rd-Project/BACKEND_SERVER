package com.example.stock.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.util.Map;

@Getter
public class StockThresholdEvent extends DomainEvent {

    private final Long stockItemId;
    private final Long itemId;
    private final int remainingQuantity;
    private final int totalQuantity;

    public StockThresholdEvent(Long stockItemId, Long itemId, int remainingQuantity, int totalQuantity) {
        super("stock-events");
        this.stockItemId = stockItemId;
        this.itemId = itemId;
        this.remainingQuantity = remainingQuantity;
        this.totalQuantity = totalQuantity;
    }

    @Override
    public String getEventTypeName() {
        return "STOCK_THRESHOLD_REACHED";
    }

    @Override
    public Map<String, Object> getPayload() {
        return Map.of(
                "stockItemId", stockItemId,
                "itemId", itemId,
                "remainingQuantity", remainingQuantity,
                "totalQuantity", totalQuantity
        );
    }
}
