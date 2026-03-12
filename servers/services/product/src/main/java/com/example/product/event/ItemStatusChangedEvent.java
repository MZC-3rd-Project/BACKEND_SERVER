package com.example.product.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class ItemStatusChangedEvent extends DomainEvent {

    private final Long itemId;
    private final String previousStatus;
    private final String newStatus;
    private final String itemType;
    private final Long sellerId;
    private final Long storeId;

    public ItemStatusChangedEvent(
            Long itemId,
            String previousStatus,
            String newStatus,
            String itemType,
            Long sellerId,
            Long storeId
    ) {
        super("item-events");
        this.itemId = itemId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.itemType = itemType;
        this.sellerId = sellerId;
        this.storeId = storeId;
    }

    @Override
    public String getEventTypeName() {
        return "ITEM_STATUS_CHANGED";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("itemId", itemId);
        payload.put("previousStatus", previousStatus);
        payload.put("newStatus", newStatus);
        payload.put("itemType", itemType);
        payload.put("sellerId", sellerId);
        payload.put("storeId", storeId);
        return payload;
    }
}
