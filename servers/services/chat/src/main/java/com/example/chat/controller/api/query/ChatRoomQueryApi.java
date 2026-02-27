package com.example.chat.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.chat.dto.query.response.ChatMessageItemResponse;
import com.example.chat.dto.query.response.ChatRoomSummaryResponse;
import com.example.core.pagination.CursorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Chat Room Query", description = "채팅 조회 API")
public interface ChatRoomQueryApi {

    @Operation(summary = "내 채팅방 목록 조회")
    @GetMapping("/rooms")
    ApiResponse<CursorResponse<ChatRoomSummaryResponse>> findMyRooms(
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    );

    @Operation(summary = "채팅방 메시지 목록 조회")
    @GetMapping("/rooms/{roomId}/messages")
    ApiResponse<CursorResponse<ChatMessageItemResponse>> findRoomMessages(
            @PathVariable Long roomId,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size
    );
}
