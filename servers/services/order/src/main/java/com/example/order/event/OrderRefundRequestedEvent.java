package com.example.order.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.util.Map;

@Getter
public class OrderRefundRequestedEvent extends DomainEvent {

    private final Long orderId;
    private final Long userId;
    private final Long totalAmount;

    public OrderRefundRequestedEvent(Long orderId, Long userId, Long totalAmount) {
        super("order-events");
        this.orderId = orderId;
        this.userId = userId;
        this.totalAmount = totalAmount;
    }

    @Override
    public String getEventTypeName() {
        return "ORDER_REFUND_REQUESTED_EVENT";
    }

    @Override
    public Map<String, Object> getPayload() {
        return Map.of(
                "orderId", orderId,
                "userId", userId,
                "totalAmount", totalAmount
        );
    }
}
