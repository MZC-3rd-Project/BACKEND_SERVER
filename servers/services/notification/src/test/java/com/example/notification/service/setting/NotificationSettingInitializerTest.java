package com.example.notification.service.setting;

import com.example.notification.entity.NotificationChannel;
import com.example.notification.entity.NotificationSetting;
import com.example.notification.entity.NotificationType;
import com.example.notification.entity.NotificationUserPreference;
import com.example.notification.repository.NotificationSettingRepository;
import com.example.notification.repository.NotificationUserPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationSettingInitializerTest {

    @Mock
    private NotificationSettingRepository notificationSettingRepository;

    @Mock
    private NotificationUserPreferenceRepository notificationUserPreferenceRepository;

    @Mock
    private NotificationSettingPolicy notificationSettingPolicy;

    @InjectMocks
    private NotificationSettingInitializer notificationSettingInitializer;

    @Test
    void ensurePreference_persistsDefaultWhenMissing() {
        NotificationUserPreference created = NotificationUserPreference.createDefault(1L);
        when(notificationUserPreferenceRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(notificationUserPreferenceRepository.save(any(NotificationUserPreference.class))).thenReturn(created);

        NotificationUserPreference result = notificationSettingInitializer.ensurePreference(1L);

        assertEquals("Asia/Seoul", result.getTimezone());
        verify(notificationUserPreferenceRepository).save(any(NotificationUserPreference.class));
    }

    @Test
    void ensureSetting_persistsDefaultSettingWhenMissing() {
        NotificationUserPreference preference = NotificationUserPreference.createDefault(1L);
        NotificationSetting created = NotificationSetting.createDefault(
                1L, NotificationType.PAYMENT, NotificationChannel.EMAIL, true
        );

        when(notificationUserPreferenceRepository.findByUserId(1L)).thenReturn(Optional.of(preference));
        when(notificationSettingRepository.findByUserIdAndTypeAndChannel(
                1L, NotificationType.PAYMENT, NotificationChannel.EMAIL
        )).thenReturn(Optional.empty());
        when(notificationSettingPolicy.isDefaultEnabled(NotificationChannel.EMAIL)).thenReturn(true);
        when(notificationSettingRepository.save(any(NotificationSetting.class))).thenReturn(created);

        NotificationSetting result = notificationSettingInitializer.ensureSetting(
                1L, NotificationType.PAYMENT, NotificationChannel.EMAIL
        );

        assertEquals(NotificationType.PAYMENT, result.getType());
        assertEquals(NotificationChannel.EMAIL, result.getChannel());
        verify(notificationSettingRepository).save(any(NotificationSetting.class));
    }
}
