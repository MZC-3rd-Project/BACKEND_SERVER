package com.example.notification.service.query;

import com.example.notification.dto.setting.response.NotificationGlobalPreferenceResponse;
import com.example.notification.dto.setting.response.NotificationSettingItemResponse;
import com.example.notification.dto.setting.response.NotificationSettingsResponse;
import com.example.notification.entity.NotificationSetting;
import com.example.notification.entity.NotificationUserPreference;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class NotificationSettingQueryAssembler {

    public NotificationSettingsView toView(NotificationUserPreference preference, List<NotificationSetting> settings) {
        List<NotificationSettingsView.NotificationSettingItemView> itemViews = settings.stream()
                .sorted(Comparator
                        .comparing(NotificationSetting::getType)
                        .thenComparing(NotificationSetting::getChannel))
                .map(setting -> new NotificationSettingsView.NotificationSettingItemView(
                        setting.getType().name(),
                        setting.getChannel().name(),
                        setting.isEnabled(),
                        setting.getMutedUntil()
                ))
                .toList();

        NotificationSettingsView.NotificationGlobalPreferenceView preferenceView =
                new NotificationSettingsView.NotificationGlobalPreferenceView(
                        preference.isGlobalEnabled(),
                        preference.getTimezone(),
                        preference.isQuietHoursEnabled(),
                        preference.getQuietHoursStart(),
                        preference.getQuietHoursEnd()
                );

        return new NotificationSettingsView(preferenceView, itemViews);
    }

    public NotificationSettingsResponse toResponse(NotificationSettingsView view) {
        NotificationGlobalPreferenceResponse preferenceResponse = NotificationGlobalPreferenceResponse.builder()
                .globalEnabled(view.preference().globalEnabled())
                .timezone(view.preference().timezone())
                .quietHoursEnabled(view.preference().quietHoursEnabled())
                .quietHoursStart(view.preference().quietHoursStart())
                .quietHoursEnd(view.preference().quietHoursEnd())
                .build();

        List<NotificationSettingItemResponse> itemResponses = view.settings().stream()
                .map(setting -> NotificationSettingItemResponse.builder()
                        .type(setting.type())
                        .channel(setting.channel())
                        .enabled(setting.enabled())
                        .mutedUntil(setting.mutedUntil())
                        .build())
                .toList();

        return NotificationSettingsResponse.of(preferenceResponse, itemResponses);
    }
}
