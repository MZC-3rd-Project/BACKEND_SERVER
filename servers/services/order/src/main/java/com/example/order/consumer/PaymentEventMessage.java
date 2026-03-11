package com.example.order.consumer;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class PaymentEventMessage {

    private String eventId;
    private String eventType;
    private Long paymentId;
    private Long orderId;
    private Long userId;
    private Long amount;
    private LocalDateTime paidAt;
    private String failReason;
}
