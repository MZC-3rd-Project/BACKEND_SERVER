package com.example.chat.controller.query;

import com.example.api.response.ApiResponse;
import com.example.chat.controller.api.query.ChatRoomQueryApi;
import com.example.chat.dto.query.response.ChatMessageItemResponse;
import com.example.chat.dto.query.response.ChatRoomSummaryResponse;
import com.example.chat.service.query.ChatRoomQueryService;
import com.example.core.pagination.CursorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatRoomQueryController implements ChatRoomQueryApi {

    private final ChatRoomQueryService chatRoomQueryService;

    @Override
    public ApiResponse<CursorResponse<ChatRoomSummaryResponse>> findMyRooms(Long userId, String cursor, int size) {
        return ApiResponse.success(chatRoomQueryService.findMyRooms(userId, cursor, size));
    }

    @Override
    public ApiResponse<CursorResponse<ChatMessageItemResponse>> findRoomMessages(Long roomId,
                                                                                  Long userId,
                                                                                  String cursor,
                                                                                  int size) {
        return ApiResponse.success(chatRoomQueryService.findRoomMessages(roomId, userId, cursor, size));
    }
}
