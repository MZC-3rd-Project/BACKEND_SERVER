package com.example.chat.controller.command;

import com.example.api.response.ApiResponse;
import com.example.chat.controller.api.command.ChatMessageCommandApi;
import com.example.chat.dto.command.request.CreateChatMessageRequest;
import com.example.chat.dto.command.response.ChatMessageSendResponse;
import com.example.chat.service.command.ChatMessageCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatMessageCommandController implements ChatMessageCommandApi {

    private final ChatMessageCommandService chatMessageCommandService;

    @Override
    public ApiResponse<ChatMessageSendResponse> sendMessage(Long roomId, CreateChatMessageRequest request, Long senderId) {
        return ApiResponse.success(chatMessageCommandService.sendMessage(roomId, request, senderId));
    }
}
