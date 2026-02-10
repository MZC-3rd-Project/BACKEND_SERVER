package com.example.product.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.util.Map;

@Getter
public class ItemUpdatedEvent extends DomainEvent {

    private final Long itemId;
    private final String title;
    private final Long price;

    public ItemUpdatedEvent(Long itemId, String title, Long price) {
        super("item-events");
        this.itemId = itemId;
        this.title = title;
        this.price = price;
    }

    @Override
    public String getEventTypeName() {
        return "ITEM_UPDATED";
    }

    @Override
    public Map<String, Object> getPayload() {
        return Map.of(
                "itemId", itemId,
                "title", title,
                "price", price
        );
    }
}
