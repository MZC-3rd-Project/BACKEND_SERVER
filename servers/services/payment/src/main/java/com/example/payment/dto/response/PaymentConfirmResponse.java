package com.example.payment.dto.response;

import com.example.payment.domain.Payment;

import java.time.LocalDateTime;

public record PaymentConfirmResponse(
        Long paymentId,
        Long orderId,
        String status,
        Long amount,
        String paymentKey,
        String method,
        LocalDateTime paidAt
) {
    public static PaymentConfirmResponse from(Payment payment) {
        return new PaymentConfirmResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getStatus().name(),
                payment.getAmount(),
                payment.getPaymentKey(),
                payment.getMethod(),
                payment.getPaidAt()
        );
    }
}
