package com.example.chat.consumer;

import com.example.event.consumer.EventEnvelope;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FundingEventMessage implements EventEnvelope {

    private String eventId;
    private String eventType;

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
