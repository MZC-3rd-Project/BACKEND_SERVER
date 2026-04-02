package com.example.config.kafka;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "dead_letter_messages",
        indexes = {
                @Index(name = "idx_dlm_status", columnList = "status"),
                @Index(name = "idx_dlm_topic_created", columnList = "topic, created_at"),
                @Index(name = "idx_dlm_event_id", columnList = "event_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeadLetterMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "partition_num")
    private Integer partition;

    @Column(name = "offset_num")
    private Long offset;

    @Column(name = "key_value")
    private String key;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "event_id")
    private String eventId;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "consumer_attempt_count", nullable = false)
    private int consumerAttemptCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DlqStatus status;

    @Column(name = "retry_count")
    private int retryCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_retried_at")
    private LocalDateTime lastRetriedAt;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "consumer_failure_alerted_at")
    private LocalDateTime consumerFailureAlertedAt;

    @Column(name = "retry_failure_alerted_at")
    private LocalDateTime retryFailureAlertedAt;

    public static DeadLetterMessage create(String topic, Integer partition, Long offset,
                                            String key, String payload, String errorMessage) {
        return create(topic, partition, offset, key, payload, errorMessage, null, null, 1, null);
    }

    public static DeadLetterMessage create(String topic, Integer partition, Long offset,
                                            String key, String payload, String errorMessage,
                                            String eventId, String eventType,
                                            int consumerAttemptCount,
                                            LocalDateTime nextRetryAt) {
        DeadLetterMessage dlm = new DeadLetterMessage();
        dlm.topic = topic;
        dlm.partition = partition;
        dlm.offset = offset;
        dlm.key = key;
        dlm.payload = payload;
        dlm.errorMessage = errorMessage;
        dlm.eventId = eventId;
        dlm.eventType = eventType;
        dlm.consumerAttemptCount = Math.max(1, consumerAttemptCount);
        dlm.status = DlqStatus.UNRESOLVED;
        dlm.retryCount = 0;
        dlm.createdAt = LocalDateTime.now();
        dlm.nextRetryAt = nextRetryAt;
        return dlm;
    }

    public void recordRetryFailure(String topic,
                                   Integer partition,
                                   Long offset,
                                   String key,
                                   String payload,
                                   String errorMessage,
                                   String eventType,
                                   int consumerAttemptCount,
                                   LocalDateTime nextRetryAt) {
        this.retryCount++;
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
        this.key = key;
        this.payload = payload;
        this.errorMessage = errorMessage;
        this.eventType = eventType;
        this.consumerAttemptCount = Math.max(1, consumerAttemptCount);
        this.status = DlqStatus.UNRESOLVED;
        this.nextRetryAt = nextRetryAt;
    }

    public void markAsResolved() {
        this.status = DlqStatus.RESOLVED;
        this.nextRetryAt = null;
        this.resolvedAt = LocalDateTime.now();
    }

    public void markAsRetrying(LocalDateTime lastRetriedAt, LocalDateTime nextRetryAt) {
        this.status = DlqStatus.RETRYING;
        this.lastRetriedAt = lastRetriedAt;
        this.nextRetryAt = nextRetryAt;
    }

    public boolean shouldAlertConsumerFailure(int threshold) {
        return consumerAttemptCount >= threshold && consumerFailureAlertedAt == null;
    }

    public boolean shouldAlertRetryFailure(int threshold) {
        return retryCount >= threshold && retryFailureAlertedAt == null;
    }

    public void markConsumerFailureAlerted() {
        this.consumerFailureAlertedAt = LocalDateTime.now();
    }

    public void markRetryAlerted() {
        this.retryFailureAlertedAt = LocalDateTime.now();
    }

    public enum DlqStatus {
        UNRESOLVED,
        RETRYING,
        RESOLVED
    }
}
