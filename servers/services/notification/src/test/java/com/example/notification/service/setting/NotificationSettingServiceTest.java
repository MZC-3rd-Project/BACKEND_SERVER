package com.example.notification.service.setting;

import com.example.core.exception.BusinessException;
import com.example.notification.dto.setting.request.UpdateNotificationGlobalPreferenceRequest;
import com.example.notification.dto.setting.request.UpdateNotificationSettingRequest;
import com.example.notification.entity.NotificationChannel;
import com.example.notification.entity.NotificationSetting;
import com.example.notification.entity.NotificationType;
import com.example.notification.entity.NotificationUserPreference;
import com.example.notification.repository.NotificationSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
    private NotificationSettingInitializer notificationSettingInitializer;

    @InjectMocks
    private NotificationSettingService notificationSettingService;

    @Test
    void shouldSendNotification_returnsFalseWhenGlobalDisabled() {
        NotificationUserPreference preference = NotificationUserPreference.createDefault(1L);
        preference.updateGlobalEnabled(false);
        when(notificationSettingInitializer.ensurePreference(1L)).thenReturn(preference);

        boolean sendable = notificationSettingService.shouldSendNotification(
                1L, NotificationType.PAYMENT, NotificationChannel.EMAIL
        );

        assertFalse(sendable);
        verify(notificationSettingRepository, never()).findByUserIdAndTypeAndChannel(anyLong(), any(), any());
    }

    @Test
    void shouldSendNotification_usesDefaultPolicyWhenNoUserSetting() {
        NotificationUserPreference preference = NotificationUserPreference.createDefault(1L);
        NotificationSetting setting = NotificationSetting.createDefault(
                1L, NotificationType.STOCK_DEPLETED, NotificationChannel.SMS, false
        );
        when(notificationSettingInitializer.ensurePreference(1L)).thenReturn(preference);
        when(notificationSettingInitializer.ensureSetting(
                1L, NotificationType.STOCK_DEPLETED, NotificationChannel.SMS
        )).thenReturn(setting);

        boolean sendable = notificationSettingService.shouldSendNotification(
                1L, NotificationType.STOCK_DEPLETED, NotificationChannel.SMS
        );

        assertFalse(sendable);
        verify(notificationSettingInitializer).ensureSetting(1L, NotificationType.STOCK_DEPLETED, NotificationChannel.SMS);
    }

    @Test
    void updateGlobalPreference_usesInitializerPreference() {
        NotificationUserPreference preference = NotificationUserPreference.createDefault(1L);
        when(notificationSettingInitializer.ensurePreference(1L)).thenReturn(preference);

        UpdateNotificationGlobalPreferenceRequest request = new UpdateNotificationGlobalPreferenceRequest();
        ReflectionTestUtils.setField(request, "globalEnabled", false);

        notificationSettingService.updateGlobalPreference(1L, request);

        assertFalse(preference.isGlobalEnabled());
        verify(notificationSettingInitializer).ensurePreference(1L);
    }

    @Test
    void upsertSetting_throwsWhenNoFieldToUpdate() {
        NotificationSetting existing = NotificationSetting.createDefault(
                1L, NotificationType.GENERAL, NotificationChannel.EMAIL, true
        );
        when(notificationSettingInitializer.ensureSetting(
                1L, NotificationType.GENERAL, NotificationChannel.EMAIL
        )).thenReturn(existing);

        UpdateNotificationSettingRequest request = new UpdateNotificationSettingRequest();
        ReflectionTestUtils.setField(request, "type", "GENERAL");
        ReflectionTestUtils.setField(request, "channel", "EMAIL");

        assertThrows(BusinessException.class, () -> notificationSettingService.upsertSetting(1L, request));
    }
}
