package com.example.notification.controller.command;

import com.example.api.response.ApiResponse;
import com.example.notification.controller.api.command.NotificationCommandApi;
import com.example.notification.dto.command.request.CreateNotificationRequest;
import com.example.notification.dto.command.response.NotificationDispatchResponse;
import com.example.notification.service.command.NotificationCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationCommandController implements NotificationCommandApi {

    private final NotificationCommandService notificationCommandService;

    @Override
    public ApiResponse<NotificationDispatchResponse> createAndSend(
            @Valid @RequestBody CreateNotificationRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.success(notificationCommandService.createAndSend(request, userId));
    }
}
