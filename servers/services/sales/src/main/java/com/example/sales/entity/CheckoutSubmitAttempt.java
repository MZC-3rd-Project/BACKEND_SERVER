package com.example.sales.entity;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "checkout_submit_attempts",
        indexes = {
                @Index(name = "idx_checkout_submit_attempt_order_id", columnList = "order_id"),
                @Index(name = "idx_checkout_submit_attempt_status_next", columnList = "status, next_retry_at"),
                @Index(name = "idx_checkout_submit_attempt_session", columnList = "checkout_session_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class CheckoutSubmitAttempt extends BaseEntity {

    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @SnowflakeGenerated
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkout_session_id", nullable = false)
    private CheckoutSession checkoutSession;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CheckoutSubmitAttemptStatus status;

    @Lob
    @Column(name = "request_payload_json")
    private String requestPayloadJson;

    @Lob
    @Column(name = "response_payload_json")
    private String responsePayloadJson;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "last_error_message", length = MAX_ERROR_LENGTH)
    private String lastErrorMessage;

    public static CheckoutSubmitAttempt createRequested(
            CheckoutSession checkoutSession,
            Long orderId,
            String requestPayloadJson
    ) {
        CheckoutSubmitAttempt attempt = new CheckoutSubmitAttempt();
        attempt.checkoutSession = checkoutSession;
        attempt.orderId = orderId;
        attempt.status = CheckoutSubmitAttemptStatus.REQUESTED;
        attempt.requestPayloadJson = requestPayloadJson;
        attempt.retryCount = 0;
        return attempt;
    }

    public void markSucceeded(String responsePayloadJson) {
        status = CheckoutSubmitAttemptStatus.SUCCEEDED;
        responsePayloadJson(responsePayloadJson);
        lastErrorMessage = null;
        nextRetryAt = null;
    }

    public void scheduleRetry(String errorMessage, LocalDateTime nextRetryAt) {
        retryCount += 1;
        status = CheckoutSubmitAttemptStatus.FAILED;
        this.lastErrorMessage = normalizeError(errorMessage);
        this.nextRetryAt = nextRetryAt;
    }

    public void markFailed(String errorMessage) {
        retryCount += 1;
        status = CheckoutSubmitAttemptStatus.FAILED;
        lastErrorMessage = normalizeError(errorMessage);
        nextRetryAt = null;
    }

    private void responsePayloadJson(String responsePayloadJson) {
        this.responsePayloadJson = responsePayloadJson;
    }

    private static String normalizeError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "Checkout submit failed";
        }
        return errorMessage.length() <= MAX_ERROR_LENGTH
                ? errorMessage
                : errorMessage.substring(0, MAX_ERROR_LENGTH);
    }
}
