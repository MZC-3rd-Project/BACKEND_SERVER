package com.example.notification.service.query;

import com.example.notification.dto.setting.response.NotificationSettingsResponse;
import com.example.notification.entity.NotificationChannel;
import com.example.notification.entity.NotificationSetting;
import com.example.notification.entity.NotificationType;
import com.example.notification.entity.NotificationUserPreference;
import com.example.notification.repository.NotificationSettingRepository;
import com.example.notification.repository.NotificationUserPreferenceRepository;
import com.example.notification.service.setting.NotificationSettingPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationSettingQueryServiceTest {

    @Mock
    private NotificationSettingRepository notificationSettingRepository;

    @Mock
    private NotificationUserPreferenceRepository notificationUserPreferenceRepository;

    @Mock
    private NotificationSettingPolicy notificationSettingPolicy;

    @Spy
    private NotificationSettingQueryAssembler notificationSettingQueryAssembler;

    @InjectMocks
    private NotificationSettingQueryService notificationSettingQueryService;

    @Test
    void getSettings_returnsInMemoryDefaultsWithoutPersisting() {
        when(notificationUserPreferenceRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(notificationSettingRepository.findByUserId(1L)).thenReturn(List.of());
        when(notificationSettingPolicy.supportedChannels()).thenReturn(List.of(NotificationChannel.EMAIL));
        when(notificationSettingPolicy.isDefaultEnabled(NotificationChannel.EMAIL)).thenReturn(true);

        NotificationSettingsResponse response = notificationSettingQueryService.getSettings(1L);

        assertEquals("Asia/Seoul", response.getPreference().getTimezone());
        assertFalse(response.getSettings().isEmpty());
        assertEquals(NotificationType.values().length, response.getSettings().size());
        verify(notificationUserPreferenceRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(notificationSettingRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(notificationSettingRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getSettings_preservesPersistedSettingAndFillsMissingDefaultsInMemory() {
        NotificationUserPreference preference = NotificationUserPreference.createDefault(1L);
        NotificationSetting persisted = NotificationSetting.createDefault(
                1L, NotificationType.GENERAL, NotificationChannel.EMAIL, false
        );

        when(notificationUserPreferenceRepository.findByUserId(1L)).thenReturn(Optional.of(preference));
        when(notificationSettingRepository.findByUserId(1L)).thenReturn(List.of(persisted));
        when(notificationSettingPolicy.supportedChannels()).thenReturn(List.of(NotificationChannel.EMAIL));
        when(notificationSettingPolicy.isDefaultEnabled(NotificationChannel.EMAIL)).thenReturn(true);

        NotificationSettingsResponse response = notificationSettingQueryService.getSettings(1L);

        assertEquals(NotificationType.values().length, response.getSettings().size());
        long disabledCount = response.getSettings().stream()
                .filter(setting -> setting.getType().equals(NotificationType.GENERAL.name()))
                .filter(setting -> setting.getChannel().equals(NotificationChannel.EMAIL.name()))
                .filter(setting -> !setting.isEnabled())
                .count();
        assertEquals(1L, disabledCount);
        verify(notificationSettingRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }
}
