package com.example.notification.controller.query;

import com.example.api.response.ApiResponse;
import com.example.notification.controller.api.query.NotificationSettingQueryApi;
import com.example.notification.dto.setting.response.NotificationSettingsResponse;
import com.example.notification.service.setting.NotificationSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationSettingQueryController implements NotificationSettingQueryApi {

    private final NotificationSettingService notificationSettingService;

    @Override
    public ApiResponse<NotificationSettingsResponse> getMySettings(Long userId) {
        return ApiResponse.success(notificationSettingService.getSettings(userId));
    }
}
