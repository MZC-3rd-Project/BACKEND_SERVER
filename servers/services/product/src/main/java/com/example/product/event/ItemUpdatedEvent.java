package com.example.product.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class ItemUpdatedEvent extends DomainEvent {

    private final Long itemId;
    private final String title;
    private final Long price;
    private final Long thumbnailMediaId;
    private final Long mediaVersion;

    public ItemUpdatedEvent(Long itemId, String title, Long price, Long thumbnailMediaId, Long mediaVersion) {
        super("item-events");
        this.itemId = itemId;
        this.title = title;
        this.price = price;
        this.thumbnailMediaId = thumbnailMediaId;
        this.mediaVersion = mediaVersion;
    }

    @Override
    public String getEventTypeName() {
        return "ITEM_UPDATED";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("itemId", itemId);
        payload.put("title", title);
        payload.put("price", price);
        payload.put("thumbnailMediaId", thumbnailMediaId);
        payload.put("mediaVersion", mediaVersion);
        return payload;
    }
}
