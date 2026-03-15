package com.example.chat.service.query;

import com.example.chat.dto.query.response.ChatMessageItemResponse;
import com.example.chat.dto.query.response.ChatRoomLastMessageResponse;
import com.example.chat.entity.message.ChatMessage;
import org.springframework.stereotype.Component;

@Component
public class ChatMessagePresenter {

    private static final int LAST_MESSAGE_PREVIEW_LIMIT = 100;

    public ChatMessageItemResponse toItemResponse(ChatMessage message) {
        return ChatMessageItemResponse.builder()
            .messageId(message.getId())
            .senderId(message.getSenderId())
            .messageType(message.getMessageType())
            .content(message.getContentSanitized())
            .createdAt(message.getCreatedAt())
            .build();
    }

    public ChatRoomLastMessageResponse toLastMessageResponse(ChatMessage message) {
        return ChatRoomLastMessageResponse.builder()
            .messageId(message.getId())
            .messageType(message.getMessageType())
            .preview(toPreview(message.getContentSanitized()))
            .createdAt(message.getCreatedAt())
            .build();
    }

    private String toPreview(String content) {
        if (content == null || content.length() <= LAST_MESSAGE_PREVIEW_LIMIT) {
            return content;
        }
        return content.substring(0, LAST_MESSAGE_PREVIEW_LIMIT);
    }
}
