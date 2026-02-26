package com.example.notification.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.notification.dto.setting.response.NotificationSettingsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Notification Setting Query", description = "알림 설정 조회 API")
public interface NotificationSettingQueryApi {

    @Operation(summary = "내 알림 설정 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/settings")
    ApiResponse<NotificationSettingsResponse> getMySettings(
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long userId);
}
