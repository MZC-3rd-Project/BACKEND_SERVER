package com.example.funding.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class FundingRefundedEvent extends DomainEvent {

    private final Long campaignId;
    private final Long participationId;
    private final Long orderId;
    private final Long userId;
    private final Long amount;

    public FundingRefundedEvent(Long campaignId, Long participationId, Long orderId,
                                 Long userId, Long amount) {
        super("funding-events");
        this.campaignId = campaignId;
        this.participationId = participationId;
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
    }

    @Override
    public String getEventTypeName() {
        return "FUNDING_REFUNDED";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("campaignId", campaignId);
        payload.put("participationId", participationId);
        payload.put("orderId", orderId);
        payload.put("userId", userId);
        payload.put("amount", amount);
        return payload;
    }
}
