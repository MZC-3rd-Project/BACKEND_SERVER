package com.example.chat.dto.query.response;

import com.example.chat.entity.message.ChatMessageType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatMessageItemResponse {

    private Long messageId;
    private Long senderId;
    private ChatMessageType messageType;
    private String content;
    private LocalDateTime createdAt;
}
