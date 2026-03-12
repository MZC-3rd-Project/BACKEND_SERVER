package com.example.product.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class ItemDeletedEvent extends DomainEvent {

    private final Long itemId;
    private final String itemType;
    private final String status;
    private final Long sellerId;
    private final Long storeId;

    public ItemDeletedEvent(
            Long itemId,
            String itemType,
            String status,
            Long sellerId,
            Long storeId
    ) {
        super("item-events");
        this.itemId = itemId;
        this.itemType = itemType;
        this.status = status;
        this.sellerId = sellerId;
        this.storeId = storeId;
    }

    @Override
    public String getEventTypeName() {
        return "ITEM_DELETED";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("itemId", itemId);
        payload.put("itemType", itemType);
        payload.put("status", status);
        payload.put("sellerId", sellerId);
        payload.put("storeId", storeId);
        return payload;
    }
}
