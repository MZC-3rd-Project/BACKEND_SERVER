package com.example.hotdeal.consumer.order;

import com.example.event.consumer.EventEnvelope;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class HotDealOrderEventMessage implements EventEnvelope {

    private String eventId;
    private String eventType;
    private Long orderId;
    private Long userId;
}
