package com.example.sales.entity;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
@Entity
@Table(name = "checkout_sessions",
        indexes = {
                @Index(name = "idx_checkout_session_order_id", columnList = "order_id", unique = true),
                @Index(name = "idx_checkout_session_status_expires", columnList = "status, expires_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_checkout_session_user_idempotency", columnNames = {"user_id", "idempotency_key"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class CheckoutSession extends BaseEntity {

    private static final int MAX_ERROR_CODE_LENGTH = 50;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CheckoutSessionStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "reserved_at")
    private LocalDateTime reservedAt;

    @Column(name = "quoted_at")
    private LocalDateTime quotedAt;

    @Column(name = "submit_requested_at")
    private LocalDateTime submitRequestedAt;

    @Column(name = "order_created_at")
    private LocalDateTime orderCreatedAt;

    @Column(name = "last_error_code", length = MAX_ERROR_CODE_LENGTH)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = MAX_ERROR_MESSAGE_LENGTH)
    private String lastErrorMessage;

    @OneToMany(mappedBy = "checkoutSession", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private final List<CheckoutSessionLineItem> lineItems = new ArrayList<>();

    public static CheckoutSession createReserved(Long orderId, Long userId, String idempotencyKey, LocalDateTime expiresAt) {
        CheckoutSession session = new CheckoutSession();
        session.orderId = orderId;
        session.userId = userId;
        session.idempotencyKey = idempotencyKey;
        session.status = CheckoutSessionStatus.RESERVED;
        session.expiresAt = expiresAt;
        session.reservedAt = LocalDateTime.now();
        return session;
    }

    public void addLineItem(CheckoutSessionLineItem lineItem) {
        lineItems.add(lineItem);
        lineItem.attachTo(this);
    }

    public void markQuoted(LocalDateTime quotedAt) {
        changeStatus(CheckoutSessionStatus.QUOTED);
        this.quotedAt = quotedAt == null ? LocalDateTime.now() : quotedAt;
        clearLastError();
    }

    public void refreshQuotedAt(LocalDateTime quotedAt) {
        this.quotedAt = quotedAt == null ? LocalDateTime.now() : quotedAt;
    }

    public void markSubmitting() {
        changeStatus(CheckoutSessionStatus.SUBMITTING);
        submitRequestedAt = LocalDateTime.now();
        clearLastError();
    }

    public void markOrderCreated() {
        changeStatus(CheckoutSessionStatus.ORDER_CREATED);
        orderCreatedAt = LocalDateTime.now();
        clearLastError();
    }

    public void markExpired() {
        changeStatus(CheckoutSessionStatus.EXPIRED);
    }

    public void markFailed(String errorCode, String errorMessage) {
        changeStatus(CheckoutSessionStatus.FAILED);
        lastErrorCode = normalizeErrorCode(errorCode);
        lastErrorMessage = normalizeErrorMessage(errorMessage);
    }

    public void cancel() {
        changeStatus(CheckoutSessionStatus.CANCELLED);
    }

    private void changeStatus(CheckoutSessionStatus newStatus) {
        status.validateTransitionTo(newStatus);
        status = newStatus;
    }

    private void clearLastError() {
        lastErrorCode = null;
        lastErrorMessage = null;
    }

    private static String normalizeErrorCode(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return null;
        }
        return trimToLength(errorCode, MAX_ERROR_CODE_LENGTH);
    }

    private static String normalizeErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return null;
        }
        return trimToLength(errorMessage, MAX_ERROR_MESSAGE_LENGTH);
    }

    private static String trimToLength(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public boolean hasSameReserveIntent(List<CheckoutSessionLineItem> candidateLineItems) {
        if (lineItems.size() != candidateLineItems.size()) {
            return false;
        }

        for (int i = 0; i < lineItems.size(); i++) {
            CheckoutSessionLineItem existing = lineItems.get(i);
            CheckoutSessionLineItem candidate = candidateLineItems.get(i);
            if (!existing.hasSameReserveIntent(candidate)) {
                return false;
            }
        }
        return true;
    }
}
