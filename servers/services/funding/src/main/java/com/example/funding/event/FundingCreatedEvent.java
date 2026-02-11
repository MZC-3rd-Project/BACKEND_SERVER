package com.example.funding.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Getter
public class FundingCreatedEvent extends DomainEvent {

    private final Long campaignId;
    private final Long itemId;
    private final Long sellerId;
    private final String fundingType;
    private final Long goalAmount;
    private final LocalDateTime startAt;
    private final LocalDateTime endAt;

    public FundingCreatedEvent(Long campaignId, Long itemId, Long sellerId,
                                String fundingType, Long goalAmount,
                                LocalDateTime startAt, LocalDateTime endAt) {
        super("funding-events");
        this.campaignId = campaignId;
        this.itemId = itemId;
        this.sellerId = sellerId;
        this.fundingType = fundingType;
        this.goalAmount = goalAmount;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    @Override
    public String getEventTypeName() {
        return "FUNDING_CREATED";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("campaignId", campaignId);
        payload.put("itemId", itemId);
        payload.put("sellerId", sellerId);
        payload.put("fundingType", fundingType);
        payload.put("goalAmount", goalAmount);
        payload.put("startAt", startAt.toString());
        payload.put("endAt", endAt.toString());
        return payload;
    }
}
