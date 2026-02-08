package com.example.config.kafka;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_events",
        indexes = @Index(name = "idx_event_id", columnList = "eventId", unique = true))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProcessingStatus status;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "error_message")
    private String errorMessage;

    public enum ProcessingStatus {
        PROCESSING, PROCESSED, FAILED
    }

    public static ProcessedEvent create(String eventId, String eventType) {
        ProcessedEvent event = new ProcessedEvent();
        event.eventId = eventId;
        event.eventType = eventType;
        event.status = ProcessingStatus.PROCESSING;
        return event;
    }

    public void markAsProcessed() {
        this.status = ProcessingStatus.PROCESSED;
        this.processedAt = LocalDateTime.now();
    }

    public void markAsFailed(String errorMessage) {
        this.status = ProcessingStatus.FAILED;
        this.errorMessage = errorMessage;
    }
}
