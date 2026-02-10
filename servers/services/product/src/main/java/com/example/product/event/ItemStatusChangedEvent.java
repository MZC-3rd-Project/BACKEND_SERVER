package com.example.product.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.util.Map;

@Getter
public class ItemStatusChangedEvent extends DomainEvent {

    private final Long itemId;
    private final String previousStatus;
    private final String newStatus;

    public ItemStatusChangedEvent(Long itemId, String previousStatus, String newStatus) {
        super("item-events");
        this.itemId = itemId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
    }

    @Override
    public String getEventTypeName() {
        return "ITEM_STATUS_CHANGED";
    }

    @Override
    public Map<String, Object> getPayload() {
        return Map.of(
                "itemId", itemId,
                "previousStatus", previousStatus,
                "newStatus", newStatus
        );
    }
}
