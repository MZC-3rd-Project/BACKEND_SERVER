package com.example.analyticsdashboard.consumer.search;

import com.example.event.consumer.EventEnvelope;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AnalyticsSearchEventMessage implements EventEnvelope {

    private String eventId;
    private String eventType;
    private String occurredAt;

    private Long itemId;
    private Long storeId;
    private Long sellerId;
    private Long userId;

    private String queryHash;
    private String sessionId;
    private String journeyId;
    private String correlationId;
    private String causationId;
}
