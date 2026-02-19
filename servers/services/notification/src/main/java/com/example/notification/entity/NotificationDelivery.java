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
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_deliveries",
        indexes = {
                @Index(name = "idx_notification_deliveries_retry",
                        columnList = "status, next_retry_at"),
                @Index(name = "idx_notification_deliveries_notification_id",
                        columnList = "notification_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_notification_deliveries_notification_channel",
                        columnNames = {"notification_id", "channel"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class NotificationDelivery extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationDeliveryStatus status;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "provider_message_id", length = 100)
    private String providerMessageId;

    @Column(name = "recipient_address", length = 320)
    private String recipientAddress;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error_code", length = 50)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = 500)
    private String lastErrorMessage;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    public static NotificationDelivery createPending(Long notificationId,
                                                     NotificationChannel channel,
                                                     String provider,
                                                     String recipientAddress) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.notificationId = notificationId;
        delivery.channel = channel;
        delivery.status = NotificationDeliveryStatus.PENDING;
        delivery.provider = provider;
        delivery.recipientAddress = recipientAddress;
        delivery.attemptCount = 0;
        return delivery;
    }

    public void markPending() {
        this.status = NotificationDeliveryStatus.PENDING;
        this.nextRetryAt = null;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
    }

    public void markSent(String providerMessageId) {
        this.status = NotificationDeliveryStatus.SENT;
        this.providerMessageId = providerMessageId;
        this.attemptCount += 1;
        this.nextRetryAt = null;
    }

    public void updateProvider(String provider) {
        if (provider != null && !provider.isBlank()) {
            this.provider = provider;
        }
    }

    public void updateRecipientAddress(String recipientAddress) {
        if (recipientAddress != null && !recipientAddress.isBlank()) {
            this.recipientAddress = recipientAddress;
        }
    }

    public void markDelivered() {
        this.status = NotificationDeliveryStatus.DELIVERED;
        this.deliveredAt = LocalDateTime.now();
        this.nextRetryAt = null;
    }

    public void markRetry(String errorCode, String errorMessage, LocalDateTime nextRetryAt) {
        this.status = NotificationDeliveryStatus.RETRYING;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
        this.nextRetryAt = nextRetryAt;
        this.attemptCount += 1;
    }

    public void markFailed(String errorCode, String errorMessage) {
        this.status = NotificationDeliveryStatus.FAILED;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
        this.nextRetryAt = null;
        this.attemptCount += 1;
    }

    public void markDropped(String errorCode, String errorMessage) {
        this.status = NotificationDeliveryStatus.DROPPED;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
        this.nextRetryAt = null;
    }

    public void markBounced(String errorCode, String errorMessage) {
        this.status = NotificationDeliveryStatus.BOUNCED;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
        this.nextRetryAt = null;
    }
}
