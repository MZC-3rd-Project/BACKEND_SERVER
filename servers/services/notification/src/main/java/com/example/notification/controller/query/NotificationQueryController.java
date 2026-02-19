package com.example.notification.controller.query;

import com.example.api.response.ApiResponse;
import com.example.core.pagination.CursorResponse;
import com.example.notification.controller.api.query.NotificationQueryApi;
import com.example.notification.dto.query.response.NotificationHistoryItemResponse;
import com.example.notification.dto.query.response.UnreadCountResponse;
import com.example.notification.service.query.NotificationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationQueryController implements NotificationQueryApi {

    private final NotificationQueryService notificationQueryService;

    @Override
    public ApiResponse<CursorResponse<NotificationHistoryItemResponse>> getMyNotifications(
            Long userId, String cursor, int size) {
        return ApiResponse.success(notificationQueryService.findMyNotifications(userId, cursor, size));
    }

    @Override
    public ApiResponse<UnreadCountResponse> getUnreadCount(Long userId) {
        return ApiResponse.success(notificationQueryService.getUnreadCount(userId));
    }
}
