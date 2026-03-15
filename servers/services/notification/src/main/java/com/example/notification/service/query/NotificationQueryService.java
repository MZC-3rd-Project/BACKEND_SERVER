package com.example.notification.service.query;

import com.example.core.pagination.CursorResponse;
import com.example.notification.dto.query.response.NotificationHistoryItemResponse;
import com.example.notification.dto.query.response.UnreadCountResponse;
import com.example.notification.service.unread.NotificationUnreadCountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService {

    private final NotificationHistoryReader notificationHistoryReader;
    private final NotificationHistoryAssembler notificationHistoryAssembler;
    private final NotificationUnreadCountService notificationUnreadCountService;

    public CursorResponse<NotificationHistoryItemResponse> findMyNotifications(Long userId, String cursor, int size) {
        CursorResponse<NotificationHistoryView> views = notificationHistoryReader.findMyNotifications(userId, cursor, size);
        return notificationHistoryAssembler.toResponse(views);
    }

    public UnreadCountResponse getUnreadCount(Long userId) {
        long unreadCount = notificationUnreadCountService.getOrLoad(userId);
        return UnreadCountResponse.of(unreadCount);
    }
}
