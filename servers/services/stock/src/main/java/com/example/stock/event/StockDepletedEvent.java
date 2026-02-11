package com.example.stock.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.util.Map;

@Getter
public class StockDepletedEvent extends DomainEvent {

    private final Long stockItemId;
    private final Long itemId;

    public StockDepletedEvent(Long stockItemId, Long itemId) {
        super("stock-events");
        this.stockItemId = stockItemId;
        this.itemId = itemId;
    }

    @Override
    public String getEventTypeName() {
        return "STOCK_DEPLETED";
    }

    @Override
    public Map<String, Object> getPayload() {
        return Map.of(
                "stockItemId", stockItemId,
                "itemId", itemId
        );
    }
}
