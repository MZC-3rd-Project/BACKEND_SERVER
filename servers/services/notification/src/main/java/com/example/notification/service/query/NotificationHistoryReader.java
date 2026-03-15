package com.example.notification.service.query;

import com.example.core.pagination.CursorResponse;
import com.example.core.pagination.CursorUtils;
import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationDelivery;
import com.example.notification.repository.NotificationDeliveryRepository;
import com.example.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationHistoryReader {

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;

    public CursorResponse<NotificationHistoryView> findMyNotifications(Long userId, String cursor, int size) {
        Long cursorId = CursorUtils.decodeLong(cursor);
        PageRequest pageable = PageRequest.of(0, size + 1);

        List<Notification> notifications = notificationRepository.findByRecipientIdWithCursor(userId, cursorId, pageable);
        boolean hasNext = notifications.size() > size;
        List<Notification> pageItems = hasNext ? notifications.subList(0, size) : notifications;
        Map<Long, List<NotificationDelivery>> deliveriesByNotificationId = loadDeliveries(pageItems);

        List<NotificationHistoryView> views = pageItems.stream()
                .map(notification -> toView(
                        notification,
                        deliveriesByNotificationId.getOrDefault(notification.getId(), List.of())
                ))
                .toList();

        String nextCursor = hasNext
                ? CursorUtils.encode(pageItems.get(pageItems.size() - 1).getId())
                : null;
        return CursorResponse.of(views, nextCursor);
    }

    private Map<Long, List<NotificationDelivery>> loadDeliveries(List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> notificationIds = notifications.stream()
                .map(Notification::getId)
                .toList();
        List<NotificationDelivery> deliveries = notificationDeliveryRepository
                .findByNotificationIdInOrderByNotificationIdAscIdAsc(notificationIds);
        return deliveries.stream()
                .collect(Collectors.groupingBy(NotificationDelivery::getNotificationId));
    }

    private NotificationHistoryView toView(Notification notification, List<NotificationDelivery> deliveries) {
        return new NotificationHistoryView(
                notification.getId(),
                notification.getType().name(),
                notification.getChannel().name(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getReferenceType(),
                notification.getReferenceId(),
                notification.isRead(),
                notification.getReadAt(),
                notification.getCreatedAt(),
                deliveries.stream()
                        .map(delivery -> new NotificationHistoryView.NotificationDeliveryView(
                                delivery.getChannel().name(),
                                delivery.getStatus().name(),
                                delivery.getProvider(),
                                delivery.getAttemptCount(),
                                delivery.getLastErrorCode(),
                                delivery.getLastErrorMessage(),
                                delivery.getNextRetryAt(),
                                delivery.getDeliveredAt()
                        ))
                        .toList()
        );
    }
}
