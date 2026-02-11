package com.example.stock.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.util.Map;

@Getter
public class StockDecreasedEvent extends DomainEvent {

    private final Long stockItemId;
    private final Long itemId;
    private final int quantity;
    private final int remainingQuantity;

    public StockDecreasedEvent(Long stockItemId, Long itemId, int quantity, int remainingQuantity) {
        super("stock-events");
        this.stockItemId = stockItemId;
        this.itemId = itemId;
        this.quantity = quantity;
        this.remainingQuantity = remainingQuantity;
    }

    @Override
    public String getEventTypeName() {
        return "STOCK_DECREASED";
    }

    @Override
    public Map<String, Object> getPayload() {
        return Map.of(
                "stockItemId", stockItemId,
                "itemId", itemId,
                "quantity", quantity,
                "remainingQuantity", remainingQuantity
        );
    }
}
