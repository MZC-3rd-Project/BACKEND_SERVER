package com.example.notification.service.setting;

import com.example.core.exception.BusinessException;
import com.example.notification.dto.setting.request.UpdateNotificationSettingRequest;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationSettingServiceTest {

    @Mock
    private NotificationSettingRepository notificationSettingRepository;

    @Mock
    private NotificationUserPreferenceRepository notificationUserPreferenceRepository;

    @Mock
    private NotificationSettingPolicy notificationSettingPolicy;

    @InjectMocks
    private NotificationSettingService notificationSettingService;

    @Test
    void shouldSendNotification_returnsFalseWhenGlobalDisabled() {
        NotificationUserPreference preference = NotificationUserPreference.createDefault(1L);
        preference.updateGlobalEnabled(false);
        when(notificationUserPreferenceRepository.findByUserId(1L)).thenReturn(Optional.of(preference));

        boolean sendable = notificationSettingService.shouldSendNotification(
                1L, NotificationType.PAYMENT, NotificationChannel.EMAIL
        );

        assertFalse(sendable);
        verify(notificationSettingRepository, never()).findByUserIdAndTypeAndChannel(anyLong(), any(), any());
    }

    @Test
    void shouldSendNotification_usesDefaultPolicyWhenNoUserSetting() {
        NotificationUserPreference preference = NotificationUserPreference.createDefault(1L);
        when(notificationUserPreferenceRepository.findByUserId(1L)).thenReturn(Optional.of(preference));
        when(notificationSettingRepository.findByUserIdAndTypeAndChannel(
                1L, NotificationType.STOCK_DEPLETED, NotificationChannel.SMS
        )).thenReturn(Optional.empty());
        when(notificationSettingPolicy.isDefaultEnabled(NotificationChannel.SMS)).thenReturn(false);

        boolean sendable = notificationSettingService.shouldSendNotification(
                1L, NotificationType.STOCK_DEPLETED, NotificationChannel.SMS
        );

        assertFalse(sendable);
        verify(notificationSettingPolicy).isDefaultEnabled(NotificationChannel.SMS);
    }

    @Test
    void upsertSetting_throwsWhenNoFieldToUpdate() {
        NotificationSetting existing = NotificationSetting.createDefault(
                1L, NotificationType.GENERAL, NotificationChannel.EMAIL, true
        );
        when(notificationSettingRepository.findByUserIdAndTypeAndChannel(
                1L, NotificationType.GENERAL, NotificationChannel.EMAIL
        )).thenReturn(Optional.of(existing));

        UpdateNotificationSettingRequest request = new UpdateNotificationSettingRequest();
        ReflectionTestUtils.setField(request, "type", "GENERAL");
        ReflectionTestUtils.setField(request, "channel", "EMAIL");

        assertThrows(BusinessException.class, () -> notificationSettingService.upsertSetting(1L, request));
    }
}
