package com.example.search.consumer.funding;

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
    private String fundingType;
    private Long goalAmount;
    private Long currentAmount;
    private Integer currentQuantity;
}
