package com.example.notification.dto.setting.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class NotificationSettingsResponse {

    private NotificationGlobalPreferenceResponse preference;
    private List<NotificationSettingItemResponse> settings;

    public static NotificationSettingsResponse of(NotificationGlobalPreferenceResponse preference,
                                                  List<NotificationSettingItemResponse> settings) {
        return NotificationSettingsResponse.builder()
                .preference(preference)
                .settings(settings)
                .build();
    }
}
