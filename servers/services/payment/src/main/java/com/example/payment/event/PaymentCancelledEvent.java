package com.example.payment.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class PaymentCancelledEvent extends DomainEvent {

    private final Long paymentId;
    private final Long orderId;
    private final Long userId;
    private final Long amount;

    public PaymentCancelledEvent(Long paymentId, Long orderId, Long userId, Long amount) {
        super("payment-events");
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
    }

    @Override
    public String getEventTypeName() {
        return "PAYMENT_CANCELLED";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentId", paymentId);
        payload.put("orderId", orderId);
        payload.put("userId", userId);
        payload.put("amount", amount);
        return payload;
    }
}
