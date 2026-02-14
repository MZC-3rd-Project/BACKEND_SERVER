package com.example.event.outbox;

import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_messages",
        indexes = {
                @Index(name = "idx_outbox_status_created", columnList = "status, createdAt"),
                @Index(name = "idx_outbox_status_updated", columnList = "status, updatedAt"),
                @Index(name = "idx_outbox_aggregate", columnList = "aggregateType, aggregateId"),
                @Index(name = "idx_outbox_event_id", columnList = "eventId", unique = true)
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxStatus status;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "retry_count")
    private int retryCount;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "causation_id")
    private String causationId;

    public static OutboxMessage create(String eventId, String aggregateType, String aggregateId,
                                        String topic, String eventType, String payload) {
        return create(eventId, aggregateType, aggregateId, topic, eventType, payload, null, null);
    }

    public static OutboxMessage create(String eventId, String aggregateType, String aggregateId,
                                        String topic, String eventType, String payload,
                                        String correlationId, String causationId) {
        OutboxMessage message = new OutboxMessage();
        message.eventId = eventId;
        message.aggregateType = aggregateType;
        message.aggregateId = aggregateId;
        message.topic = topic;
        message.eventType = eventType;
        message.payload = payload;
        message.status = OutboxStatus.PENDING;
        message.retryCount = 0;
        message.correlationId = correlationId;
        message.causationId = causationId;
        return message;
    }

    public void markAsSending() {
        this.status = OutboxStatus.SENDING;
    }

    public void markAsPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public void revertToPending() {
        this.status = OutboxStatus.PENDING;
    }

    public void markAsFailed(String errorMessage) {
        this.status = OutboxStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public boolean exceedsMaxRetries(int maxRetries) {
        return this.retryCount >= maxRetries;
    }

    @PostPersist
    private void onPostPersist() {
        DomainEventPublisher.publish(new OutboxSavedEvent(this));
    }
}
