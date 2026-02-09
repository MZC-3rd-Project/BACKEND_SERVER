package com.example.product.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.util.Map;

@Getter
public class ItemCreatedEvent extends DomainEvent {

    private final Long itemId;
    private final String title;
    private final String itemType;
    private final Long sellerId;

    public ItemCreatedEvent(Long itemId, String title, String itemType, Long sellerId) {
        super("item-events");
        this.itemId = itemId;
        this.title = title;
        this.itemType = itemType;
        this.sellerId = sellerId;
    }

    @Override
    public String getEventTypeName() {
        return "ITEM_CREATED";
    }

    @Override
    public Map<String, Object> getPayload() {
        return Map.of(
                "itemId", itemId,
                "title", title,
                "itemType", itemType,
                "sellerId", sellerId
        );
    }
}
