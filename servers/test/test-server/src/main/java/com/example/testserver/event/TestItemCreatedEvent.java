package com.example.testserver.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.util.Map;

@Getter
public class TestItemCreatedEvent extends DomainEvent {

    private final Long itemId;
    private final String itemName;

    public TestItemCreatedEvent(Long itemId, String itemName) {
        super("test-item-events");
        this.itemId = itemId;
        this.itemName = itemName;
    }

    @Override
    public String getEventTypeName() {
        return "TEST_ITEM_CREATED";
    }

    @Override
    public Map<String, Object> getPayload() {
        return Map.of(
                "itemId", itemId,
                "itemName", itemName
        );
    }
}
