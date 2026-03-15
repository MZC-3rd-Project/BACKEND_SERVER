package com.example.notification.service.query;

import com.example.notification.dto.setting.response.NotificationSettingsResponse;
import com.example.notification.entity.NotificationChannel;
import com.example.notification.entity.NotificationSetting;
import com.example.notification.entity.NotificationType;
import com.example.notification.entity.NotificationUserPreference;
import com.example.notification.repository.NotificationSettingRepository;
import com.example.notification.repository.NotificationUserPreferenceRepository;
import com.example.notification.service.setting.NotificationSettingPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationSettingQueryService {

    private final NotificationSettingRepository notificationSettingRepository;
    private final NotificationUserPreferenceRepository notificationUserPreferenceRepository;
    private final NotificationSettingPolicy notificationSettingPolicy;
    private final NotificationSettingQueryAssembler notificationSettingQueryAssembler;

    public NotificationSettingsResponse getSettings(Long userId) {
        NotificationUserPreference preference = notificationUserPreferenceRepository.findByUserId(userId)
                .orElseGet(() -> NotificationUserPreference.createDefault(userId));
        List<NotificationSetting> settings = mergeWithDefaults(userId, notificationSettingRepository.findByUserId(userId));
        NotificationSettingsView view = notificationSettingQueryAssembler.toView(preference, settings);
        return notificationSettingQueryAssembler.toResponse(view);
    }

    private List<NotificationSetting> mergeWithDefaults(Long userId, List<NotificationSetting> persistedSettings) {
        Map<NotificationType, Map<NotificationChannel, NotificationSetting>> settingMap = new EnumMap<>(NotificationType.class);
        for (NotificationSetting setting : persistedSettings) {
            settingMap
                    .computeIfAbsent(setting.getType(), ignored -> new EnumMap<>(NotificationChannel.class))
                    .put(setting.getChannel(), setting);
        }

        List<NotificationSetting> merged = new ArrayList<>(persistedSettings);
        for (NotificationType type : NotificationType.values()) {
            for (NotificationChannel channel : notificationSettingPolicy.supportedChannels()) {
                Map<NotificationChannel, NotificationSetting> channels = settingMap.get(type);
                if (channels == null || !channels.containsKey(channel)) {
                    merged.add(NotificationSetting.createDefault(
                            userId,
                            type,
                            channel,
                            notificationSettingPolicy.isDefaultEnabled(channel)
                    ));
                }
            }
        }
        return merged;
    }
}
