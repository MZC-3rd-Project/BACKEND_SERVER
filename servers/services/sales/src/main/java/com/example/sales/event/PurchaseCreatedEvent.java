package com.example.sales.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class PurchaseCreatedEvent extends DomainEvent {

    private final Long purchaseId;
    private final Long orderId;
    private final Long userId;
    private final Long itemId;
    private final Long totalAmount;
    private final Integer quantity;

    public PurchaseCreatedEvent(Long purchaseId, Long orderId, Long userId,
                                 Long itemId, Long totalAmount, Integer quantity) {
        super("sales-events");
        this.purchaseId = purchaseId;
        this.orderId = orderId;
        this.userId = userId;
        this.itemId = itemId;
        this.totalAmount = totalAmount;
        this.quantity = quantity;
    }

    @Override
    public String getEventTypeName() {
        return "PURCHASE_CREATED";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("purchaseId", purchaseId);
        payload.put("orderId", orderId);
        payload.put("userId", userId);
        payload.put("itemId", itemId);
        payload.put("totalAmount", totalAmount);
        payload.put("quantity", quantity);
        return payload;
    }
}
