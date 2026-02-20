package com.example.chat.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.chat.dto.command.request.CreateChatMessageRequest;
import com.example.chat.dto.command.response.ChatMessageSendResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Chat Message Command", description = "채팅 메시지 전송 API")
public interface ChatMessageCommandApi {

    @Operation(summary = "채팅 메시지 전송")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "전송 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청/읽기전용 방"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음")
    })
    @PostMapping("/rooms/{roomId}/messages")
    ApiResponse<ChatMessageSendResponse> sendMessage(
            @PathVariable Long roomId,
            @Valid @RequestBody CreateChatMessageRequest request,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long senderId
    );
}
