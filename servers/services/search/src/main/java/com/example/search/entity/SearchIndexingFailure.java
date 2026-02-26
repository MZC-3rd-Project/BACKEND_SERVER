package com.example.search.entity;

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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "search_indexing_failures",
        indexes = {
                @Index(name = "idx_search_indexing_failures_event", columnList = "event_id,event_type"),
                @Index(name = "idx_search_indexing_failures_status", columnList = "status")
        }
)
public class SearchIndexingFailure extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SearchIndexingFailureStatus status;

    @Column(name = "last_retried_at")
    private LocalDateTime lastRetriedAt;

    @Builder
    private SearchIndexingFailure(
            String eventId,
            String eventType,
            Long itemId,
            String payload,
            String failureReason,
            Integer retryCount,
            SearchIndexingFailureStatus status,
            LocalDateTime lastRetriedAt
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.itemId = itemId;
        this.payload = payload;
        this.failureReason = failureReason;
        this.retryCount = retryCount;
        this.status = status;
        this.lastRetriedAt = lastRetriedAt;
    }

    public void markPending(String reason) {
        this.failureReason = reason;
        this.status = SearchIndexingFailureStatus.PENDING;
    }

    public void markRetrying() {
        this.status = SearchIndexingFailureStatus.RETRYING;
        this.retryCount = this.retryCount == null ? 1 : this.retryCount + 1;
        this.lastRetriedAt = LocalDateTime.now();
    }

    public void markResolved() {
        this.status = SearchIndexingFailureStatus.RESOLVED;
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        this.status = SearchIndexingFailureStatus.FAILED;
        this.failureReason = reason;
    }
}
