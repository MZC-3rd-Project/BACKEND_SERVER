package com.example.notification.controller.command;

import com.example.api.response.ApiResponse;
import com.example.notification.controller.api.command.NotificationActionCommandApi;
import com.example.notification.dto.command.response.ReadAllResultResponse;
import com.example.notification.service.command.NotificationActionCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationActionCommandController implements NotificationActionCommandApi {

    private final NotificationActionCommandService notificationActionCommandService;

    @Override
    public ApiResponse<Void> markAsRead(Long userId, Long notificationId) {
        notificationActionCommandService.markAsRead(userId, notificationId);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<ReadAllResultResponse> markAllAsRead(Long userId) {
        int updated = notificationActionCommandService.markAllAsRead(userId);
        return ApiResponse.success(ReadAllResultResponse.of(updated));
    }

    @Override
    public ApiResponse<Void> delete(Long userId, Long notificationId) {
        notificationActionCommandService.deleteNotification(userId, notificationId);
        return ApiResponse.success();
    }
}
