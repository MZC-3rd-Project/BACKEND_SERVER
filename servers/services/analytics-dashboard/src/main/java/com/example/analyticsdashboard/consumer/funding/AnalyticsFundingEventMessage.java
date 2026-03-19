package com.example.analyticsdashboard.consumer.funding;

import com.example.event.consumer.EventEnvelope;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AnalyticsFundingEventMessage implements EventEnvelope {

    private String eventId;
    private String eventType;
    private String occurredAt;

    private Long campaignId;
    private Long itemId;
    private Long sellerId;

    private Long participationId;
    private Long orderId;
    private Long userId;
    private Long amount;
    private Integer quantity;
    private String fundingType;
}
