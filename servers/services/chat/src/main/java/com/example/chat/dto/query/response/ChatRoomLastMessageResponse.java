package com.example.chat.dto.query.response;

import com.example.chat.entity.message.ChatMessageType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatRoomLastMessageResponse {

    private Long messageId;
    private ChatMessageType messageType;
    private String preview;
    private LocalDateTime createdAt;
}
