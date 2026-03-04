package com.example.order.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class OrderCreatedEvent extends DomainEvent {

    private final Long orderId;
    private final Long purchaseId;
    private final Long userId;
    private final Long totalAmount;
    private final Long reservationId;
    private final List<Map<String, Object>> items;

    public OrderCreatedEvent(Long orderId, Long purchaseId, Long userId,
                              Long totalAmount, Long reservationId,
                              List<Map<String, Object>> items) {
        super("order-events");
        this.orderId = orderId;
        this.purchaseId = purchaseId;
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.reservationId = reservationId;
        this.items = items;
    }

    @Override
    public String getEventTypeName() {
        return "ORDER_CREATED";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("purchaseId", purchaseId);
        payload.put("userId", userId);
        payload.put("totalAmount", totalAmount);
        payload.put("reservationId", reservationId);
        payload.put("items", items);
        return payload;
    }
}
