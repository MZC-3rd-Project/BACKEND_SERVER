package com.example.notification.dto.setting.response;

import com.example.notification.entity.NotificationUserPreference;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalTime;

@Getter
@Builder
public class NotificationGlobalPreferenceResponse {

    private boolean globalEnabled;
    private String timezone;
    private boolean quietHoursEnabled;
    private LocalTime quietHoursStart;
    private LocalTime quietHoursEnd;

    public static NotificationGlobalPreferenceResponse from(NotificationUserPreference preference) {
        return NotificationGlobalPreferenceResponse.builder()
                .globalEnabled(preference.isGlobalEnabled())
                .timezone(preference.getTimezone())
                .quietHoursEnabled(preference.isQuietHoursEnabled())
                .quietHoursStart(preference.getQuietHoursStart())
                .quietHoursEnd(preference.getQuietHoursEnd())
                .build();
    }
}
