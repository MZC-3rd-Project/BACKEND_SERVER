package com.example.notification.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.notification.dto.command.request.CreateNotificationRequest;
import com.example.notification.dto.command.response.NotificationDispatchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Notification Command", description = "알림 생성/발송 API")
public interface NotificationCommandApi {

    @Operation(summary = "알림 생성 및 발송")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "처리 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청")
    })
    @PostMapping
    ApiResponse<NotificationDispatchResponse> createAndSend(
            @Valid @RequestBody CreateNotificationRequest request,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long userId
    );
}
