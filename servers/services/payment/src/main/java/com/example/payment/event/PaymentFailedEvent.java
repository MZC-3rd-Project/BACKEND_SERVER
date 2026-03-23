package com.example.payment.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class PaymentFailedEvent extends DomainEvent {

    private final Long paymentId;
    private final Long orderId;
    private final Long userId;
    private final Long amount;
    private final String failReason;

    public PaymentFailedEvent(Long paymentId, Long orderId, Long userId, Long amount, String failReason) {
        super("payment-events");
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.failReason = failReason;
    }

    @Override
    public String getEventTypeName() {
        return "PAYMENT_FAILED";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentId", paymentId);
        payload.put("orderId", orderId);
        payload.put("userId", userId);
        payload.put("amount", amount);
        payload.put("failReason", failReason);
        return payload;
    }
}
