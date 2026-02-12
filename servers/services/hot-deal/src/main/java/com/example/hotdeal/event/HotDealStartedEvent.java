package com.example.hotdeal.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Getter
public class HotDealStartedEvent extends DomainEvent {

    private final Long hotDealId;
    private final Long itemId;
    private final String title;
    private final Long discountedPrice;
    private final Integer discountRate;
    private final Integer maxQuantity;
    private final LocalDateTime startAt;
    private final LocalDateTime endAt;

    public HotDealStartedEvent(Long hotDealId, Long itemId, String title,
                                Long discountedPrice, Integer discountRate,
                                Integer maxQuantity, LocalDateTime startAt, LocalDateTime endAt) {
        super("hotdeal-events");
        this.hotDealId = hotDealId;
        this.itemId = itemId;
        this.title = title;
        this.discountedPrice = discountedPrice;
        this.discountRate = discountRate;
        this.maxQuantity = maxQuantity;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    @Override
    public String getEventTypeName() {
        return "HOT_DEAL_STARTED";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("hotDealId", hotDealId);
        payload.put("itemId", itemId);
        payload.put("title", title);
        payload.put("discountedPrice", discountedPrice);
        payload.put("discountRate", discountRate);
        payload.put("maxQuantity", maxQuantity);
        payload.put("startAt", startAt.toString());
        payload.put("endAt", endAt.toString());
        return payload;
    }
}
