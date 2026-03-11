package com.example.order.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class OrderStatusChangedEvent extends DomainEvent {

    private final String statusEventType;
    private final Long orderId;
    private final Long userId;
    private final List<Map<String, Object>> lineItems;

    public OrderStatusChangedEvent(String statusEventType, Long orderId, Long userId,
                                    List<Map<String, Object>> lineItems) {
        super("order-events");
        this.statusEventType = statusEventType;
        this.orderId = orderId;
        this.userId = userId;
        this.lineItems = lineItems;
    }

    @Override
    public String getEventTypeName() {
        return statusEventType;
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("userId", userId);
        payload.put("lineItems", lineItems);
        return payload;
    }
}
