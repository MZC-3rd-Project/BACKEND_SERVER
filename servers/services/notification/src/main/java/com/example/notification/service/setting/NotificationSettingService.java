package com.example.notification.service.setting;

import com.example.core.exception.BusinessException;
import com.example.notification.dto.setting.request.UpdateNotificationGlobalPreferenceRequest;
import com.example.notification.dto.setting.request.UpdateNotificationSettingRequest;
import com.example.notification.dto.setting.response.NotificationGlobalPreferenceResponse;
import com.example.notification.dto.setting.response.NotificationSettingItemResponse;
import com.example.notification.entity.NotificationChannel;
import com.example.notification.entity.NotificationSetting;
import com.example.notification.entity.NotificationType;
import com.example.notification.entity.NotificationUserPreference;
import com.example.notification.exception.NotificationErrorCode;
import com.example.notification.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class NotificationSettingService {

    private final NotificationSettingRepository notificationSettingRepository;
    private final NotificationSettingInitializer notificationSettingInitializer;

    @Transactional
    public NotificationGlobalPreferenceResponse updateGlobalPreference(Long userId,
                                                                       UpdateNotificationGlobalPreferenceRequest request) {
        NotificationUserPreference preference = notificationSettingInitializer.ensurePreference(userId);

        if (request.getGlobalEnabled() != null) {
            preference.updateGlobalEnabled(request.getGlobalEnabled());
        }

        if (request.getTimezone() != null && !request.getTimezone().isBlank()) {
            validateTimezone(request.getTimezone());
            preference.updateTimezone(request.getTimezone());
        }

        if (request.getQuietHoursEnabled() != null) {
            preference.updateQuietHoursEnabled(request.getQuietHoursEnabled());
        }

        boolean quietHoursRequested = request.getQuietHoursStart() != null || request.getQuietHoursEnd() != null;
        if (quietHoursRequested || preference.isQuietHoursEnabled()) {
            LocalTime quietStart = request.getQuietHoursStart() != null
                    ? request.getQuietHoursStart()
                    : preference.getQuietHoursStart();
            LocalTime quietEnd = request.getQuietHoursEnd() != null
                    ? request.getQuietHoursEnd()
                    : preference.getQuietHoursEnd();
            validateQuietHours(quietStart, quietEnd);
            preference.updateQuietHours(quietStart, quietEnd);
        }

        return NotificationGlobalPreferenceResponse.from(preference);
    }

    @Transactional
    public NotificationSettingItemResponse upsertSetting(Long userId, UpdateNotificationSettingRequest request) {
        NotificationType type = parseType(request.getType());
        NotificationChannel channel = parseChannel(request.getChannel());

        NotificationSetting setting = notificationSettingInitializer.ensureSetting(userId, type, channel);

        boolean changed = false;
        if (request.getEnabled() != null) {
            setting.updateEnabled(request.getEnabled());
            changed = true;
        }
        if (Boolean.TRUE.equals(request.getClearMute())) {
            setting.unmute();
            changed = true;
        }
        if (request.getMutedUntil() != null) {
            setting.muteUntil(request.getMutedUntil());
            changed = true;
        }

        if (!changed) {
            throw new BusinessException(NotificationErrorCode.INVALID_SETTING_REQUEST);
        }

        NotificationSetting saved = notificationSettingRepository.save(setting);
        return NotificationSettingItemResponse.from(saved);
    }

    @Transactional
    public boolean shouldSendNotification(Long userId, NotificationType type, NotificationChannel channel) {
        NotificationUserPreference preference = notificationSettingInitializer.ensurePreference(userId);
        if (!preference.isGlobalEnabled()) {
            return false;
        }
        if (preference.isInQuietHours(LocalDateTime.now())) {
            return false;
        }

        NotificationSetting setting = notificationSettingInitializer.ensureSetting(userId, type, channel);
        return setting.isEnabledNow();
    }

    private NotificationType parseType(String rawType) {
        try {
            return NotificationType.valueOf(rawType.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            throw new BusinessException(NotificationErrorCode.INVALID_NOTIFICATION_TYPE);
        }
    }

    private NotificationChannel parseChannel(String rawChannel) {
        try {
            return NotificationChannel.valueOf(rawChannel.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            throw new BusinessException(NotificationErrorCode.INVALID_CHANNEL);
        }
    }

    private void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (Exception ignored) {
            throw new BusinessException(NotificationErrorCode.INVALID_TIMEZONE);
        }
    }

    private void validateQuietHours(LocalTime quietStart, LocalTime quietEnd) {
        if (quietStart == null || quietEnd == null) {
            throw new BusinessException(NotificationErrorCode.INVALID_QUIET_HOURS);
        }
    }

}
