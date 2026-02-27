package com.example.notification.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.notification.dto.setting.request.UpdateNotificationGlobalPreferenceRequest;
import com.example.notification.dto.setting.request.UpdateNotificationSettingRequest;
import com.example.notification.dto.setting.response.NotificationGlobalPreferenceResponse;
import com.example.notification.dto.setting.response.NotificationSettingItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Notification Setting Command", description = "알림 설정 변경 API")
public interface NotificationSettingCommandApi {

    @Operation(summary = "글로벌 알림 설정 변경")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청")
    })
    @PatchMapping("/settings/global")
    ApiResponse<NotificationGlobalPreferenceResponse> updateGlobalPreference(
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long userId,
            @RequestBody UpdateNotificationGlobalPreferenceRequest request);

    @Operation(summary = "유형/채널별 알림 설정 변경")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청")
    })
    @PatchMapping("/settings")
    ApiResponse<NotificationSettingItemResponse> updateSetting(
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long userId,
            @Valid @RequestBody UpdateNotificationSettingRequest request);
}
