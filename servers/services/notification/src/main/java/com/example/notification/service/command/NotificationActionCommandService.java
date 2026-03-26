package com.example.notification.service.command;

import com.example.core.exception.BusinessException;
import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationStatus;
import com.example.notification.exception.NotificationErrorCode;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.service.unread.NotificationUnreadCountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationActionCommandService {

    private final NotificationRepository notificationRepository;
    private final NotificationUnreadCountService notificationUnreadCountService;

    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        boolean unreadBefore = !notification.isRead();
        notification.markAsRead();
        if (unreadBefore) {
            notificationUnreadCountService.decreaseSafely(userId, 1);
        }
        log.info("Notification marked as read. userId={}, notificationId={}", userId, notificationId);
    }

    public int markAllAsRead(Long userId) {
        int updated = notificationRepository.markAllAsRead(userId, LocalDateTime.now(), NotificationStatus.READ);
        if (updated > 0) {
            notificationUnreadCountService.decreaseSafely(userId, updated);
            log.info("All notifications marked as read. userId={}, updatedCount={}", userId, updated);
        }
        return updated;
    }

    public void deleteNotification(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        boolean unreadBefore = !notification.isRead();
        notification.softDelete();
        if (unreadBefore) {
            notificationUnreadCountService.decreaseSafely(userId, 1);
        }
        log.info("Notification deleted. userId={}, notificationId={}", userId, notificationId);
    }
}
