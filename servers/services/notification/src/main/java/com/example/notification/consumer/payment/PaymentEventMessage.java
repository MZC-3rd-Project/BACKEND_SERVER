package com.example.notification.consumer.payment;

import com.example.event.consumer.EventEnvelope;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentEventMessage implements EventEnvelope {

    private String eventId;
    private String eventType;
    private Long paymentId;
    private Long purchaseId;
    private Long orderId;
    private Long userId;
    private Long itemId;
    private Long totalAmount;
    private Integer quantity;
}
