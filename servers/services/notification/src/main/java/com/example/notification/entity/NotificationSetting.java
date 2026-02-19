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
@Table(name = "notification_settings",
        indexes = {
                @Index(name = "idx_notification_settings_user", columnList = "user_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_notification_settings_user_type_channel",
                        columnNames = {"user_id", "type", "channel"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class NotificationSetting extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "muted_until")
    private LocalDateTime mutedUntil;

    public static NotificationSetting createDefault(Long userId,
                                                    NotificationType type,
                                                    NotificationChannel channel,
                                                    boolean enabled) {
        NotificationSetting setting = new NotificationSetting();
        setting.userId = userId;
        setting.type = type;
        setting.channel = channel;
        setting.enabled = enabled;
        return setting;
    }

    public void updateEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void muteUntil(LocalDateTime mutedUntil) {
        this.mutedUntil = mutedUntil;
    }

    public void unmute() {
        this.mutedUntil = null;
    }

    public boolean isEnabledNow() {
        if (!this.enabled) {
            return false;
        }
        return this.mutedUntil == null || LocalDateTime.now().isAfter(this.mutedUntil);
    }
}
