package com.example.notification.service.command;

import com.example.core.exception.BusinessException;
import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationChannel;
import com.example.notification.entity.NotificationStatus;
import com.example.notification.entity.NotificationType;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.service.unread.NotificationUnreadCountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationActionCommandServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationUnreadCountService notificationUnreadCountService;

    @InjectMocks
    private NotificationActionCommandService notificationActionCommandService;

    @Test
    void markAsRead_updatesNotificationState() {
        Notification notification = Notification.create(
                10L, 1L, NotificationType.PAYMENT, NotificationChannel.IN_APP,
                "title", "message", null, null, null, "dedupe", null
        );
        when(notificationRepository.findByIdAndRecipientId(99L, 10L)).thenReturn(Optional.of(notification));

        notificationActionCommandService.markAsRead(10L, 99L);

        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(notification.getReadAt()).isNotNull();
        verify(notificationUnreadCountService).decreaseSafely(10L, 1);
    }

    @Test
    void markAllAsRead_returnsUpdatedCount() {
        when(notificationRepository.markAllAsRead(eq(10L), any(LocalDateTime.class), eq(NotificationStatus.READ)))
                .thenReturn(5);

        int updatedCount = notificationActionCommandService.markAllAsRead(10L);

        assertThat(updatedCount).isEqualTo(5);
        verify(notificationUnreadCountService).decreaseSafely(10L, 5);
    }

    @Test
    void deleteNotification_marksSoftDelete() {
        Notification notification = Notification.create(
                10L, 1L, NotificationType.GENERAL, NotificationChannel.IN_APP,
                "title", "message", null, null, null, "dedupe-2", null
        );
        when(notificationRepository.findByIdAndRecipientId(100L, 10L)).thenReturn(Optional.of(notification));

        notificationActionCommandService.deleteNotification(10L, 100L);

        assertThat(notification.isDeleted()).isTrue();
        verify(notificationUnreadCountService).decreaseSafely(10L, 1);
    }

    @Test
    void markAsRead_throwsWhenNotificationMissing() {
        when(notificationRepository.findByIdAndRecipientId(77L, 10L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> notificationActionCommandService.markAsRead(10L, 77L));
        verify(notificationRepository).findByIdAndRecipientId(77L, 10L);
    }
}
