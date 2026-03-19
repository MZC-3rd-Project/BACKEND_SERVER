package com.example.search.event;

import com.example.event.DomainEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SearchExecutedEvent extends DomainEvent {

    private final String queryHash;
    private final String sessionId;
    private final Long userId;
    private final String journeyId;
    private final String correlationId;
    private final String causationId;
    private final List<Long> resultItemIds;

    public SearchExecutedEvent(
            String queryHash,
            String sessionId,
            Long userId,
            String journeyId,
            String correlationId,
            String causationId,
            List<Long> resultItemIds
    ) {
        super("search-events");
        this.queryHash = queryHash;
        this.sessionId = sessionId;
        this.userId = userId;
        this.journeyId = journeyId;
        this.correlationId = correlationId;
        this.causationId = causationId;
        this.resultItemIds = resultItemIds == null ? List.of() : List.copyOf(resultItemIds);
    }

    @Override
    public String getEventTypeName() {
        return "SEARCH_EXECUTED";
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
        payload.put("resultItemIds", resultItemIds);
        return payload;
    }
}
