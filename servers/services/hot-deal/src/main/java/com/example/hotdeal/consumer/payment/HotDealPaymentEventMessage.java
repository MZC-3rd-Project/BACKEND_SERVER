package com.example.hotdeal.consumer.payment;

import com.example.event.consumer.EventEnvelope;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class HotDealPaymentEventMessage implements EventEnvelope {

    private String eventId;
    private String eventType;
    private Long orderId;
    private Long userId;
}
