package com.example.notification.controller.query;

import com.example.api.response.ApiResponse;
import com.example.notification.controller.api.query.NotificationSettingQueryApi;
import com.example.notification.dto.setting.response.NotificationSettingsResponse;
import com.example.notification.service.query.NotificationSettingQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationSettingQueryController implements NotificationSettingQueryApi {

    private final NotificationSettingQueryService notificationSettingQueryService;

    @Override
    public ApiResponse<NotificationSettingsResponse> getMySettings(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.success(notificationSettingQueryService.getSettings(userId));
    }
}
