package com.example.event.inbox;

import com.example.data.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "inbox_messages",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_inbox_consumer_event",
                        columnNames = {"consumer_name", "event_id"}
                )
        },
        indexes = {
                @Index(name = "idx_inbox_status_nextretry", columnList = "status,next_retry_at"),
                @Index(name = "idx_inbox_status_lease", columnList = "status,lease_until"),
                @Index(name = "idx_inbox_consumer_status", columnList = "consumer_name,status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboxMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "consumer_name", nullable = false, length = 120)
    private String consumerName;

    @Column(name = "event_id", nullable = false, length = 160)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 160)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private InboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    public static InboxMessage createPending(
            String consumerName,
            String eventId,
            String eventType,
            String payload
    ) {
        InboxMessage message = new InboxMessage();
        message.consumerName = consumerName;
        message.eventId = eventId;
        message.eventType = eventType;
        message.payload = payload;
        message.status = InboxStatus.PENDING;
        message.retryCount = 0;
        return message;
    }

    public boolean isProcessingLeaseExpired(LocalDateTime now) {
        return status == InboxStatus.PROCESSING
                && leaseUntil != null
                && !leaseUntil.isAfter(now);
    }

    public boolean exceedsRetryLimitOnNextFailure(int maxRetryCount) {
        return retryCount + 1 > maxRetryCount;
    }

    public int nextRetryCount() {
        return retryCount + 1;
    }

    public void markProcessing(LocalDateTime now, long leaseSeconds) {
        this.status = InboxStatus.PROCESSING;
        this.leaseUntil = now.plusSeconds(Math.max(1, leaseSeconds));
        this.lastError = null;
    }

    public void markSucceeded(LocalDateTime now) {
        this.status = InboxStatus.SUCCEEDED;
        this.processedAt = now;
        this.leaseUntil = null;
        this.nextRetryAt = null;
        this.lastError = null;
    }

    public void markRetry(LocalDateTime now, LocalDateTime nextRetryAt, String errorMessage, int maxErrorLength) {
        this.status = InboxStatus.PENDING;
        this.retryCount = this.retryCount + 1;
        this.nextRetryAt = nextRetryAt;
        this.leaseUntil = null;
        this.processedAt = null;
        this.lastError = truncate(errorMessage, maxErrorLength);
    }

    public void markDead(LocalDateTime now, String errorMessage, int maxErrorLength) {
        this.status = InboxStatus.DEAD;
        this.retryCount = this.retryCount + 1;
        this.processedAt = now;
        this.nextRetryAt = null;
        this.leaseUntil = null;
        this.lastError = truncate(errorMessage, maxErrorLength);
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
