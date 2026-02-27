package com.example.notification.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.notification.dto.command.response.ReadAllResultResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Notification Action Command", description = "알림 상태/삭제 API")
public interface NotificationActionCommandApi {

    @Operation(summary = "알림 읽음 처리")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "처리 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "알림 없음")
    })
    @PatchMapping("/{notificationId}/read")
    ApiResponse<Void> markAsRead(
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long userId,
            @PathVariable Long notificationId
    );

    @Operation(summary = "전체 알림 읽음 처리")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "처리 성공")
    })
    @PatchMapping("/read-all")
    ApiResponse<ReadAllResultResponse> markAllAsRead(
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long userId
    );

    @Operation(summary = "알림 삭제 (soft delete)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "알림 없음")
    })
    @DeleteMapping("/{notificationId}")
    ApiResponse<Void> delete(
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long userId,
            @PathVariable Long notificationId
    );
}
