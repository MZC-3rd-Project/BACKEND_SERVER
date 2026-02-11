package com.example.funding.consumer;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentEventMessage {

    private String eventId;
    private String eventType;
    private Long participationId;
    private Long paymentId;
    private Long userId;
}
