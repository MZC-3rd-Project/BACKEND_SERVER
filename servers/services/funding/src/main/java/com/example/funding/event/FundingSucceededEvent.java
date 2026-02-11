package com.example.funding.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class FundingSucceededEvent extends DomainEvent {

    private final Long campaignId;
    private final Long itemId;
    private final Long sellerId;
    private final String fundingType;
    private final Long goalAmount;
    private final Long currentAmount;
    private final Integer currentQuantity;

    public FundingSucceededEvent(Long campaignId, Long itemId, Long sellerId,
                                  String fundingType, Long goalAmount,
                                  Long currentAmount, Integer currentQuantity) {
        super("funding-events");
        this.campaignId = campaignId;
        this.itemId = itemId;
        this.sellerId = sellerId;
        this.fundingType = fundingType;
        this.goalAmount = goalAmount;
        this.currentAmount = currentAmount;
        this.currentQuantity = currentQuantity;
    }

    @Override
    public String getEventTypeName() {
        return "FUNDING_SUCCEEDED";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("campaignId", campaignId);
        payload.put("itemId", itemId);
        payload.put("sellerId", sellerId);
        payload.put("fundingType", fundingType);
        payload.put("goalAmount", goalAmount);
        payload.put("currentAmount", currentAmount);
        payload.put("currentQuantity", currentQuantity);
        return payload;
    }
}
