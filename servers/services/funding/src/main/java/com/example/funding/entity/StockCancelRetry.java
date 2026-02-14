package com.example.funding.entity;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "funding_stock_cancel_retries",
        indexes = {
                @Index(name = "idx_funding_stock_cancel_retry_status_next", columnList = "status, nextRetryAt"),
                @Index(name = "idx_funding_stock_cancel_retry_status_updated", columnList = "status, updatedAt"),
                @Index(name = "idx_funding_stock_cancel_retry_participation", columnList = "participationId"),
                @Index(name = "idx_funding_stock_cancel_retry_reservation", columnList = "reservationId")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class StockCancelRetry extends BaseEntity {

    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(nullable = false)
    private Long participationId;

    @Column(nullable = false)
    private Long reservationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockCancelRetryStatus status;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private LocalDateTime nextRetryAt;

    @Column(length = MAX_ERROR_LENGTH)
    private String lastError;

    public static StockCancelRetry create(Long participationId, Long reservationId, String errorMessage) {
        StockCancelRetry retry = new StockCancelRetry();
        retry.participationId = participationId;
        retry.reservationId = reservationId;
        retry.status = StockCancelRetryStatus.PENDING;
        retry.retryCount = 0;
        retry.nextRetryAt = LocalDateTime.now();
        retry.lastError = normalizeError(errorMessage);
        return retry;
    }

    public void markProcessing() {
        this.status = StockCancelRetryStatus.PROCESSING;
    }

    public void markCompleted() {
        this.status = StockCancelRetryStatus.COMPLETED;
        this.lastError = null;
    }

    public void scheduleNextRetry(String errorMessage, long delaySeconds) {
        this.retryCount += 1;
        this.status = StockCancelRetryStatus.PENDING;
        this.nextRetryAt = LocalDateTime.now().plusSeconds(delaySeconds);
        this.lastError = normalizeError(errorMessage);
    }

    public void markFailed(String errorMessage) {
        this.retryCount += 1;
        this.status = StockCancelRetryStatus.FAILED;
        this.lastError = normalizeError(errorMessage);
    }

    private static String normalizeError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "Stock cancel retry failed";
        }
        if (errorMessage.length() <= MAX_ERROR_LENGTH) {
            return errorMessage;
        }
        return errorMessage.substring(0, MAX_ERROR_LENGTH);
    }
}
