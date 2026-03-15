package com.example.notification.service.query;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record NotificationSettingsView(
        NotificationGlobalPreferenceView preference,
        List<NotificationSettingItemView> settings
) {

    public record NotificationGlobalPreferenceView(
            boolean globalEnabled,
            String timezone,
            boolean quietHoursEnabled,
            LocalTime quietHoursStart,
            LocalTime quietHoursEnd
    ) {
    }

    public record NotificationSettingItemView(
            String type,
            String channel,
            boolean enabled,
            LocalDateTime mutedUntil
    ) {
    }
}
