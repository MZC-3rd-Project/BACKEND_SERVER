package com.example.payment.consumer.order;

import com.example.event.consumer.EventEnvelope;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class OrderEventMessage implements EventEnvelope {

    private String eventId;
    private String eventType;
    private Long orderId;
    private Long userId;
    private Long totalAmount;
    private LocalDateTime expiresAt;
}
