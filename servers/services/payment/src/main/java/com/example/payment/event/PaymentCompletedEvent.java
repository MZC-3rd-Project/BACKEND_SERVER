package com.example.payment.event;

import com.example.event.DomainEvent;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class PaymentCompletedEvent extends DomainEvent {

    private final Long paymentId;
    private final Long orderId;
    private final Long userId;
    private final Long totalAmount;
    private final LocalDateTime paidAt;

    public PaymentCompletedEvent(Long paymentId, Long orderId, Long userId, Long totalAmount, LocalDateTime paidAt) {
        super("payment-events");
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.paidAt = paidAt;
    }

    @Override
    public String getEventTypeName() {
        return "PAYMENT_COMPLETED";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentId", paymentId);
        payload.put("orderId", orderId);
        payload.put("userId", userId);
        payload.put("totalAmount", totalAmount);
        payload.put("paidAt", paidAt);
        return payload;
    }
}
