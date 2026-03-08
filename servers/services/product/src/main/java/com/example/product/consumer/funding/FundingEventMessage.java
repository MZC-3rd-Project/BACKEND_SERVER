package com.example.product.consumer.funding;

import com.example.event.consumer.EventEnvelope;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FundingEventMessage implements EventEnvelope {

    private String eventId;
    private String eventType;
    private Long itemId;
}
