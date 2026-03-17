package com.example.order.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
public class OrderCreatedEvent extends DomainEvent {

    private final Long orderId;
    private final Long userId;
    private final Long totalAmount;
    private final String recipientName;
    private final String recipientPhone;
    private final Long deliveryAddressId;
    private final String deliveryMemo;
    private final LocalDateTime expiresAt;
    private final List<OrderItemPayload> items;

    public OrderCreatedEvent(
            Long orderId,
            Long userId,
            Long totalAmount,
            String recipientName,
            String recipientPhone,
            Long deliveryAddressId,
            String deliveryMemo,
            LocalDateTime expiresAt,
            List<OrderItemPayload> items
    ) {
        super("order-events");
        this.orderId = orderId;
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.deliveryAddressId = deliveryAddressId;
        this.deliveryMemo = deliveryMemo;
        this.expiresAt = expiresAt;
        this.items = items == null ? List.of() : List.copyOf(items);
    }

    @Override
    public String getEventTypeName() {
        return "ORDER_CREATED_EVENT";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("userId", userId);
        payload.put("totalAmount", totalAmount);
        payload.put("recipientName", recipientName);
        payload.put("recipientPhone", recipientPhone);
        payload.put("deliveryAddressId", deliveryAddressId);
        payload.put("deliveryMemo", deliveryMemo);
        payload.put("expiresAt", expiresAt);
        payload.put("items", items.stream().map(item -> {
            Map<String, Object> itemPayload = new LinkedHashMap<>();
            itemPayload.put("itemId", item.itemId());
            itemPayload.put("storeId", item.storeId());
            itemPayload.put("channelType", item.channelType());
            itemPayload.put("channelRefId", item.channelRefId());
            itemPayload.put("quantity", item.quantity());
            itemPayload.put("unitPrice", item.unitPrice());
            itemPayload.put("lineAmount", item.lineAmount());
            itemPayload.put("titleSnap", item.titleSnap());
            itemPayload.put("itemTypeSnap", item.itemTypeSnap());
            return itemPayload;
        }).toList());
        return payload;
    }

    public record OrderItemPayload(
            Long itemId,
            Long storeId,
            String channelType,
            Long channelRefId,
            Integer quantity,
            Long unitPrice,
            Long lineAmount,
            String titleSnap,
            String itemTypeSnap
    ) {}
}
