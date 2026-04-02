package com.example.funding.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class FundingParticipatedEvent extends DomainEvent {

    private final Long campaignId;
    private final Long itemId;
    private final Long sellerId;
    private final Long participationId;
    private final Long orderId;
    private final Long userId;
    private final Long amount;
    private final Integer quantity;
    private final String fundingType;

    public FundingParticipatedEvent(
            Long campaignId,
            Long itemId,
            Long sellerId,
            Long participationId,
            Long orderId,
            Long userId,
            Long amount,
            Integer quantity,
            String fundingType
    ) {
        super("funding-events");
        this.campaignId = campaignId;
        this.itemId = itemId;
        this.sellerId = sellerId;
        this.participationId = participationId;
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.quantity = quantity;
        this.fundingType = fundingType;
    }

    @Override
    public String getEventTypeName() {
        return "FUNDING_PARTICIPATED";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("campaignId", campaignId);
        payload.put("itemId", itemId);
        payload.put("sellerId", sellerId);
        payload.put("participationId", participationId);
        payload.put("orderId", orderId);
        payload.put("userId", userId);
        payload.put("amount", amount);
        payload.put("quantity", quantity);
        payload.put("fundingType", fundingType);
        return payload;
    }
}
