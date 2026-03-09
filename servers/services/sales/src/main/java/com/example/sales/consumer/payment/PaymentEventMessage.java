package com.example.sales.consumer.payment;

import com.example.event.consumer.EventEnvelope;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentEventMessage implements EventEnvelope {

    private String eventId;
    private String eventType;
    private Long purchaseId;
    private Long paymentId;
    private Long userId;
}
