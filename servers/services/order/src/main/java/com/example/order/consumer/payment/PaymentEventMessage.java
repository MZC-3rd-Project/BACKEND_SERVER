package com.example.order.consumer.payment;

import com.example.event.consumer.EventEnvelope;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class PaymentEventMessage implements EventEnvelope {

    private String eventId;
    private String eventType;
    private Long paymentId;
    private Long orderId;
    private Long userId;
    private Long amount;
    private LocalDateTime paidAt;
    private String failReason;
}
