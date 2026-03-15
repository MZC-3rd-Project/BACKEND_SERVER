package com.example.notification.service.setting;

import com.example.notification.entity.NotificationChannel;
import com.example.notification.entity.NotificationSetting;
import com.example.notification.entity.NotificationType;
import com.example.notification.entity.NotificationUserPreference;
import com.example.notification.repository.NotificationSettingRepository;
import com.example.notification.repository.NotificationUserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationSettingInitializer {

    private final NotificationSettingRepository notificationSettingRepository;
    private final NotificationUserPreferenceRepository notificationUserPreferenceRepository;
    private final NotificationSettingPolicy notificationSettingPolicy;

    @Transactional
    public NotificationUserPreference ensurePreference(Long userId) {
        return notificationUserPreferenceRepository.findByUserId(userId)
                .orElseGet(() -> notificationUserPreferenceRepository.save(
                        NotificationUserPreference.createDefault(userId)
                ));
    }

    @Transactional
    public NotificationSetting ensureSetting(Long userId, NotificationType type, NotificationChannel channel) {
        ensurePreference(userId);
        return notificationSettingRepository.findByUserIdAndTypeAndChannel(userId, type, channel)
                .orElseGet(() -> notificationSettingRepository.save(
                        NotificationSetting.createDefault(
                                userId,
                                type,
                                channel,
                                notificationSettingPolicy.isDefaultEnabled(channel)
                        )
                ));
    }
}
