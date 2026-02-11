package com.example.stock.consumer;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentEventMessage {

    private String eventId;
    private String eventType;
    private Long reservationId;
    private Long userId;
}
