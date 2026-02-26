package com.example.search.event;

import com.example.event.DomainEvent;
import com.example.search.util.SearchLogMasker;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class SearchExecutedEvent extends DomainEvent {

    private static final String TOPIC = "search-events";
    private static final String EVENT_TYPE = "SEARCH_EXECUTED";

    private final Long itemId;
    private final Long storeId;
    private final Long sellerId;
    private final String queryHash;
    private final String sessionId;

    public SearchExecutedEvent(String query,
                               String sessionId,
                               Long itemId,
                               Long storeId,
                               Long sellerId) {
        super(TOPIC);
        this.itemId = itemId;
        this.storeId = storeId;
        this.sellerId = sellerId;
        this.queryHash = SearchLogMasker.keywordHash(query);
        this.sessionId = sessionId;
    }

    @Override
    public String getEventTypeName() {
        return EVENT_TYPE;
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("occurredAt", getOccurredAt().toString());
        payload.put("itemId", itemId);
        payload.put("storeId", storeId);
        payload.put("sellerId", sellerId);
        payload.put("queryHash", queryHash);
        payload.put("sessionId", sessionId);
        return payload;
    }
}
