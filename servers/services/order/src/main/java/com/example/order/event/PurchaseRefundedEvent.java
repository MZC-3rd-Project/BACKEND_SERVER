package com.example.order.event;

import com.example.event.DomainEvent;
import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class PurchaseRefundedEvent extends DomainEvent {

    private final Long purchaseId;
    private final Long orderId;
    private final Long userId;
    private final Long itemId;
    private final Long storeId;
    private final Integer quantity;
    private final Long totalAmount;
    private final LocalDateTime occurredAt;

    public PurchaseRefundedEvent(Order order, OrderItem orderItem, LocalDateTime occurredAt) {
        super("sales-events");
        this.purchaseId = orderItem.getId();
        this.orderId = order.getId();
        this.userId = order.getUserId();
        this.itemId = orderItem.getItemId();
        this.storeId = orderItem.getStoreId();
        this.quantity = orderItem.getQuantity();
        this.totalAmount = orderItem.getLineAmount();
        this.occurredAt = occurredAt;
    }

    @Override
    public String getEventTypeName() {
        return "PURCHASE_REFUNDED";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("purchaseId", purchaseId);
        payload.put("orderId", orderId);
        payload.put("userId", userId);
        payload.put("itemId", itemId);
        payload.put("storeId", storeId);
        payload.put("quantity", quantity);
        payload.put("totalAmount", totalAmount);
        payload.put("occurredAt", occurredAt);
        return payload;
    }
}
