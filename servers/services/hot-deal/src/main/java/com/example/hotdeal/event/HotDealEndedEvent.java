package com.example.hotdeal.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class HotDealEndedEvent extends DomainEvent {

    private final Long hotDealId;
    private final Long itemId;
    private final Integer soldQuantity;
    private final Integer maxQuantity;

    public HotDealEndedEvent(Long hotDealId, Long itemId,
                              Integer soldQuantity, Integer maxQuantity) {
        super("hotdeal-events");
        this.hotDealId = hotDealId;
        this.itemId = itemId;
        this.soldQuantity = soldQuantity;
        this.maxQuantity = maxQuantity;
    }

    @Override
    public String getEventTypeName() {
        return "HOT_DEAL_ENDED";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("hotDealId", hotDealId);
        payload.put("itemId", itemId);
        payload.put("soldQuantity", soldQuantity);
        payload.put("maxQuantity", maxQuantity);
        return payload;
    }
}
