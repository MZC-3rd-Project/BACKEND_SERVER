package com.example.chat.dto.command.response;

import com.example.chat.entity.message.ChatMessageType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatMessageSendResponse {

    private Long messageId;
    private Long roomId;
    private Long senderId;
    private ChatMessageType messageType;
    private String content;
    private LocalDateTime createdAt;
    private boolean duplicated;
}
