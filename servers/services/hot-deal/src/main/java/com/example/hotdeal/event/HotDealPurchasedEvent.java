package com.example.hotdeal.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class HotDealPurchasedEvent extends DomainEvent {

    private final Long hotDealId;
    private final Long userId;
    private final Long itemId;
    private final Integer quantity;
    private final Long totalAmount;

    public HotDealPurchasedEvent(Long hotDealId, Long userId, Long itemId,
                                  Integer quantity, Long totalAmount) {
        super("hotdeal-events");
        this.hotDealId = hotDealId;
        this.userId = userId;
        this.itemId = itemId;
        this.quantity = quantity;
        this.totalAmount = totalAmount;
    }

    @Override
    public String getEventTypeName() {
        return "HOT_DEAL_PURCHASED";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("hotDealId", hotDealId);
        payload.put("userId", userId);
        payload.put("itemId", itemId);
        payload.put("quantity", quantity);
        payload.put("totalAmount", totalAmount);
        return payload;
    }
}
