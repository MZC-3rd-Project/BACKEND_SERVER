package com.example.search.event;

import com.example.event.DomainEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public class SearchItemClickedEvent extends DomainEvent {

    private final Long itemId;
    private final Long storeId;
    private final Long sellerId;
    private final String queryHash;
    private final String sessionId;
    private final Long userId;
    private final String journeyId;
    private final String correlationId;
    private final String causationId;

    public SearchItemClickedEvent(
            Long itemId,
            Long storeId,
            Long sellerId,
            String queryHash,
            String sessionId,
            Long userId,
            String journeyId,
            String correlationId,
            String causationId
    ) {
        super("search-events");
        this.itemId = itemId;
        this.storeId = storeId;
        this.sellerId = sellerId;
        this.queryHash = queryHash;
        this.sessionId = sessionId;
        this.userId = userId;
        this.journeyId = journeyId;
        this.correlationId = correlationId;
        this.causationId = causationId;
    }

    @Override
    public String getEventTypeName() {
        return "SEARCH_ITEM_CLICKED";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("occurredAt", getOccurredAt().toString());
        payload.put("queryHash", queryHash);
        payload.put("sessionId", sessionId);
        payload.put("userId", userId);
        payload.put("journeyId", journeyId);
        payload.put("correlationId", correlationId);
        payload.put("causationId", causationId);
        payload.put("itemId", itemId);
        payload.put("storeId", storeId);
        payload.put("sellerId", sellerId);
        return payload;
    }
}
