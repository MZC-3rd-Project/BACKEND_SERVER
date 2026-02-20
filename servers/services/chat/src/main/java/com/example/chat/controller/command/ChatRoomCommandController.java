package com.example.chat.controller.command;

import com.example.api.response.ApiResponse;
import com.example.chat.controller.api.command.ChatRoomCommandApi;
import com.example.chat.dto.command.request.CreateInquiryRoomRequest;
import com.example.chat.dto.command.response.ChatRoomCreateResponse;
import com.example.chat.service.command.ChatRoomCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatRoomCommandController implements ChatRoomCommandApi {

    private final ChatRoomCommandService chatRoomCommandService;

    @Override
    public ApiResponse<ChatRoomCreateResponse> createInquiryRoom(CreateInquiryRoomRequest request, Long buyerId) {
        return ApiResponse.success(chatRoomCommandService.createInquiryRoom(request, buyerId));
    }
}
