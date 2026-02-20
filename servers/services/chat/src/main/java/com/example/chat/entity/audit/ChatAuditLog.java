package com.example.chat.entity.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_audit_logs", indexes = {
        @Index(name = "idx_chat_audit_logs_event_created", columnList = "event_type, created_at DESC"),
        @Index(name = "idx_chat_audit_logs_room_created", columnList = "room_id, created_at DESC"),
        @Index(name = "idx_chat_audit_logs_actor_created", columnList = "actor_id, created_at DESC")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 60)
    private ChatAuditEventType eventType;

    @Column(name = "event_payload", nullable = false, columnDefinition = "TEXT")
    private String eventPayload;

    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ChatAuditLog create(Long actorId,
                                      Long roomId,
                                      Long targetUserId,
                                      ChatAuditEventType eventType,
                                      String eventPayload,
                                      String correlationId) {
        ChatAuditLog log = new ChatAuditLog();
        log.actorId = actorId;
        log.roomId = roomId;
        log.targetUserId = targetUserId;
        log.eventType = eventType;
        log.eventPayload = eventPayload;
        log.correlationId = correlationId;
        return log;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
