package com.example.notification.controller.command;

import com.example.api.response.ApiResponse;
import com.example.notification.controller.api.command.NotificationSettingCommandApi;
import com.example.notification.dto.setting.request.UpdateNotificationGlobalPreferenceRequest;
import com.example.notification.dto.setting.request.UpdateNotificationSettingRequest;
import com.example.notification.dto.setting.response.NotificationGlobalPreferenceResponse;
import com.example.notification.dto.setting.response.NotificationSettingItemResponse;
import com.example.notification.service.setting.NotificationSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationSettingCommandController implements NotificationSettingCommandApi {

    private final NotificationSettingService notificationSettingService;

    @Override
    public ApiResponse<NotificationGlobalPreferenceResponse> updateGlobalPreference(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody UpdateNotificationGlobalPreferenceRequest request) {
        return ApiResponse.success(notificationSettingService.updateGlobalPreference(userId, request));
    }

    @Override
    public ApiResponse<NotificationSettingItemResponse> updateSetting(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody UpdateNotificationSettingRequest request) {
        return ApiResponse.success(notificationSettingService.upsertSetting(userId, request));
    }
}
