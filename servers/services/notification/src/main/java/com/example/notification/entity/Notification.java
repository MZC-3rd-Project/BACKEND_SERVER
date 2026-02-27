package com.example.notification.entity;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "notifications",
        indexes = {
                @Index(name = "idx_notifications_recipient_read_id",
                        columnList = "recipient_id, is_read, id"),
                @Index(name = "idx_notifications_external_event_id",
                        columnList = "external_event_id"),
                @Index(name = "idx_notifications_status", columnList = "status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_notifications_dedupe_key", columnNames = "dedupe_key")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class Notification extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(name = "actor_id")
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", nullable = false, length = 2000)
    private String message;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "external_event_id", length = 100)
    private String externalEventId;

    @Column(name = "dedupe_key", nullable = false, length = 160)
    private String dedupeKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    public static Notification create(Long recipientId,
                                      Long actorId,
                                      NotificationType type,
                                      NotificationChannel channel,
                                      String title,
                                      String message,
                                      String referenceType,
                                      String referenceId,
                                      String externalEventId,
                                      String dedupeKey,
                                      Map<String, Object> payload) {
        Notification notification = new Notification();
        notification.recipientId = recipientId;
        notification.actorId = actorId;
        notification.type = type;
        notification.channel = channel;
        notification.title = title;
        notification.message = message;
        notification.referenceType = referenceType;
        notification.referenceId = referenceId;
        notification.externalEventId = externalEventId;
        notification.dedupeKey = resolveDedupeKey(recipientId, type, externalEventId, referenceType, referenceId, dedupeKey);
        notification.payload = payload;
        notification.status = NotificationStatus.CREATED;
        notification.isRead = false;
        return notification;
    }

    public void markAsDispatched() {
        this.status = NotificationStatus.DISPATCHED;
        this.sentAt = LocalDateTime.now();
    }

    public void markAsFailed() {
        this.status = NotificationStatus.FAILED;
    }

    public void markAsRead() {
        if (this.isRead) {
            return;
        }
        this.isRead = true;
        this.readAt = LocalDateTime.now();
        this.status = NotificationStatus.READ;
    }

    public void markAsUnread() {
        this.isRead = false;
        this.readAt = null;
        if (this.status == NotificationStatus.READ) {
            this.status = NotificationStatus.DISPATCHED;
        }
    }

    public void archive() {
        this.status = NotificationStatus.ARCHIVED;
    }

    private static String resolveDedupeKey(Long recipientId,
                                           NotificationType type,
                                           String externalEventId,
                                           String referenceType,
                                           String referenceId,
                                           String dedupeKey) {
        if (!isBlank(dedupeKey)) {
            return dedupeKey;
        }

        String event = isBlank(externalEventId) ? "no-event" : externalEventId;
        String refType = isBlank(referenceType) ? "no-ref-type" : referenceType;
        String refId = isBlank(referenceId) ? "no-ref-id" : referenceId;
        return recipientId + ":" + type.name() + ":" + event + ":" + refType + ":" + refId;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
