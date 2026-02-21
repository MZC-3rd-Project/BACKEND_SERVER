package com.example.stock.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.util.Map;

@Getter
public class ItemAvailableStockChangedEvent extends DomainEvent {

    private final Long itemId;
    private final int availableStockTotal;
    private final long stockVersion;

    public ItemAvailableStockChangedEvent(Long itemId, int availableStockTotal, long stockVersion) {
        super("stock-events");
        this.itemId = itemId;
        this.availableStockTotal = availableStockTotal;
        this.stockVersion = stockVersion;
    }

    @Override
    public String getEventTypeName() {
        return "ITEM_AVAILABLE_STOCK_CHANGED";
    }

    @Override
    public Map<String, Object> getPayload() {
        return Map.of(
                "itemId", itemId,
                "availableStockTotal", availableStockTotal,
                "stockVersion", stockVersion,
                "occurredAt", getOccurredAt().toString()
        );
    }
}
