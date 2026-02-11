package com.example.stock.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.util.Map;

@Getter
public class StockIncreasedEvent extends DomainEvent {

    private final Long stockItemId;
    private final Long itemId;
    private final int quantity;
    private final int currentQuantity;

    public StockIncreasedEvent(Long stockItemId, Long itemId, int quantity, int currentQuantity) {
        super("stock-events");
        this.stockItemId = stockItemId;
        this.itemId = itemId;
        this.quantity = quantity;
        this.currentQuantity = currentQuantity;
    }

    @Override
    public String getEventTypeName() {
        return "STOCK_INCREASED";
    }

    @Override
    public Map<String, Object> getPayload() {
        return Map.of(
                "stockItemId", stockItemId,
                "itemId", itemId,
                "quantity", quantity,
                "currentQuantity", currentQuantity
        );
    }
}
