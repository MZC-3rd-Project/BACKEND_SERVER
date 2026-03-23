package com.example.payment.dto.response;

import com.example.payment.domain.Payment;

import java.time.LocalDateTime;

public record PaymentDetailResponse(
        Long paymentId,
        Long orderId,
        String status,
        Long amount,
        String paymentKey,
        String method,
        LocalDateTime paidAt,
        LocalDateTime failedAt,
        String failReason,
        LocalDateTime cancelledAt,
        String cancelReason,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {
    public static PaymentDetailResponse from(Payment payment) {
        return new PaymentDetailResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getStatus().name(),
                payment.getAmount(),
                payment.getPaymentKey(),
                payment.getMethod(),
                payment.getPaidAt(),
                payment.getFailedAt(),
                payment.getFailReason(),
                payment.getCancelledAt(),
                payment.getCancelReason(),
                payment.getExpiresAt(),
                payment.getCreatedAt()
        );
    }
}
