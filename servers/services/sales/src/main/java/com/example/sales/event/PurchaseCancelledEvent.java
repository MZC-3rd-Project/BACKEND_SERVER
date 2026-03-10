package com.example.sales.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class PurchaseCancelledEvent extends DomainEvent {

    private final Long purchaseId;
    private final Long orderId;
    private final Long userId;

    public PurchaseCancelledEvent(Long purchaseId, Long orderId, Long userId) {
        super("sales-events");
        this.purchaseId = purchaseId;
        this.orderId = orderId;
        this.userId = userId;
    }

    @Override
    public String getEventTypeName() {
        return "PURCHASE_CANCELLED";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("purchaseId", purchaseId);
        payload.put("orderId", orderId);
        payload.put("userId", userId);
        return payload;
    }
}
