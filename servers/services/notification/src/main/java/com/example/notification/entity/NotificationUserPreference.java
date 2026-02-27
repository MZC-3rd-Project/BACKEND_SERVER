package com.example.notification.entity;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Entity
@Table(name = "notification_user_preferences",
        indexes = {
                @Index(name = "idx_notification_user_preferences_user_id", columnList = "user_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_notification_user_preferences_user_id", columnNames = "user_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class NotificationUserPreference extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "global_enabled", nullable = false)
    private boolean globalEnabled;

    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone;

    @Column(name = "quiet_hours_enabled", nullable = false)
    private boolean quietHoursEnabled;

    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;

    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;

    public static NotificationUserPreference createDefault(Long userId) {
        NotificationUserPreference preference = new NotificationUserPreference();
        preference.userId = userId;
        preference.globalEnabled = true;
        preference.timezone = "Asia/Seoul";
        preference.quietHoursEnabled = false;
        preference.quietHoursStart = LocalTime.of(22, 0);
        preference.quietHoursEnd = LocalTime.of(8, 0);
        return preference;
    }

    public void updateGlobalEnabled(boolean globalEnabled) {
        this.globalEnabled = globalEnabled;
    }

    public void updateTimezone(String timezone) {
        this.timezone = timezone;
    }

    public void updateQuietHoursEnabled(boolean quietHoursEnabled) {
        this.quietHoursEnabled = quietHoursEnabled;
    }

    public void updateQuietHours(LocalTime quietHoursStart, LocalTime quietHoursEnd) {
        this.quietHoursStart = quietHoursStart;
        this.quietHoursEnd = quietHoursEnd;
    }

    public boolean isInQuietHours(LocalDateTime now) {
        if (!quietHoursEnabled || quietHoursStart == null || quietHoursEnd == null) {
            return false;
        }

        LocalTime localTime = resolveLocalTime(now);
        if (quietHoursStart.equals(quietHoursEnd)) {
            return true;
        }
        if (quietHoursStart.isBefore(quietHoursEnd)) {
            return !localTime.isBefore(quietHoursStart) && localTime.isBefore(quietHoursEnd);
        }
        return !localTime.isBefore(quietHoursStart) || localTime.isBefore(quietHoursEnd);
    }

    private LocalTime resolveLocalTime(LocalDateTime now) {
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(this.timezone);
        } catch (Exception ignored) {
            zoneId = ZoneId.systemDefault();
        }
        ZonedDateTime zonedDateTime = now.atZone(ZoneId.systemDefault()).withZoneSameInstant(zoneId);
        return zonedDateTime.toLocalTime();
    }
}
