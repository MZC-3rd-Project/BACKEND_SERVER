package com.example.sales.consumer;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentEventMessage {

    private String eventId;
    private String eventType;
    private Long purchaseId;
    private Long paymentId;
    private Long userId;
}
