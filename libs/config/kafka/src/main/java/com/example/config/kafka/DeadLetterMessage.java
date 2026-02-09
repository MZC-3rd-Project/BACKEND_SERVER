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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DlqStatus status;

    @Column(name = "retry_count")
    private int retryCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public static DeadLetterMessage create(String topic, Integer partition, Long offset,
                                            String key, String payload, String errorMessage) {
        return create(topic, partition, offset, key, payload, errorMessage, null, null);
    }

    public static DeadLetterMessage create(String topic, Integer partition, Long offset,
                                            String key, String payload, String errorMessage,
                                            String eventId, String eventType) {
        DeadLetterMessage dlm = new DeadLetterMessage();
        dlm.topic = topic;
        dlm.partition = partition;
        dlm.offset = offset;
        dlm.key = key;
        dlm.payload = payload;
        dlm.errorMessage = errorMessage;
        dlm.eventId = eventId;
        dlm.eventType = eventType;
        dlm.status = DlqStatus.UNRESOLVED;
        dlm.retryCount = 0;
        dlm.createdAt = LocalDateTime.now();
        return dlm;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public void markAsResolved() {
        this.status = DlqStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
    }

    public void markAsRetrying() {
        this.status = DlqStatus.RETRYING;
    }

    public enum DlqStatus {
        UNRESOLVED,
        RETRYING,
        RESOLVED
    }
}
