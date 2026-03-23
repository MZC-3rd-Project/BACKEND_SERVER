package com.example.payment.domain;

import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments",
        indexes = {
                @Index(name = "idx_payments_user_id", columnList = "user_id"),
                @Index(name = "idx_payments_status", columnList = "status"),
                @Index(name = "idx_payments_payment_key", columnList = "payment_key"),
                @Index(name = "idx_payments_status_expires_at", columnList = "status, expires_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "idx_payments_order_id", columnNames = "order_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class Payment extends BaseEntity {

    @Id
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "payment_key", length = 200)
    private String paymentKey;

    @Column(name = "toss_order_id", length = 200)
    private String tossOrderId;

    @Column(name = "method", length = 50)
    private String method;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "fail_reason", length = 500)
    private String failReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "toss_response", columnDefinition = "TEXT")
    private String tossResponse;

    public static Payment create(Long id, Long orderId, Long userId, Long amount, LocalDateTime expiresAt) {
        Payment payment = new Payment();
        payment.id = id;
        payment.orderId = orderId;
        payment.userId = userId;
        payment.status = PaymentStatus.READY;
        payment.amount = amount;
        payment.expiresAt = expiresAt;
        return payment;
    }

    public void markDone(String paymentKey, String tossOrderId, String method,
                         LocalDateTime paidAt, String tossResponse) {
        this.status.validateTransitionTo(PaymentStatus.DONE);
        this.status = PaymentStatus.DONE;
        this.paymentKey = paymentKey;
        this.tossOrderId = tossOrderId;
        this.method = method;
        this.paidAt = paidAt;
        this.tossResponse = tossResponse;
    }

    public void markFailed(String paymentKey, String failReason, String tossResponse) {
        this.status.validateTransitionTo(PaymentStatus.FAILED);
        this.status = PaymentStatus.FAILED;
        this.paymentKey = paymentKey;
        this.failReason = failReason;
        this.failedAt = LocalDateTime.now();
        this.tossResponse = tossResponse;
    }

    public void markExpired() {
        this.status.validateTransitionTo(PaymentStatus.EXPIRED);
        this.status = PaymentStatus.EXPIRED;
    }

    public void markCancelled(String cancelReason) {
        this.status.validateTransitionTo(PaymentStatus.CANCELLED);
        this.status = PaymentStatus.CANCELLED;
        this.cancelReason = cancelReason;
        this.cancelledAt = LocalDateTime.now();
    }
}
