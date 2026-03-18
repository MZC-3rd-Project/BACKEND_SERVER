package com.example.order.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.util.Map;

@Getter
public class OrderPaidEvent extends DomainEvent {

    private final Long orderId;
    private final Long userId;

    public OrderPaidEvent(Long orderId, Long userId) {
        super("order-events");
        this.orderId = orderId;
        this.userId = userId;
    }

    @Override
    public String getEventTypeName() {
        return "ORDER_PAID_EVENT";
    }

    @Override
    public Map<String, Object> getPayload() {
        return Map.of(
                "orderId", orderId,
                "userId", userId
        );
    }
}
