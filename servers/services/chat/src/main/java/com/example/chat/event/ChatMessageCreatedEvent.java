package com.example.chat.event;

import com.example.chat.entity.message.ChatMessageType;
import com.example.event.DomainEvent;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class ChatMessageCreatedEvent extends DomainEvent {

    private static final String TOPIC = "chat-message-events";
    private static final String EVENT_TYPE = "CHAT_MESSAGE_CREATED";

    private final Long roomId;
    private final Long messageId;
    private final Long senderId;
    private final String messageType;
    private final String content;
    private final LocalDateTime createdAt;

    public ChatMessageCreatedEvent(Long roomId,
                                   Long messageId,
                                   Long senderId,
                                   ChatMessageType messageType,
                                   String content,
                                   LocalDateTime createdAt) {
        super(TOPIC);
        this.roomId = roomId;
        this.messageId = messageId;
        this.senderId = senderId;
        this.messageType = messageType == null ? null : messageType.name();
        this.content = content;
        this.createdAt = createdAt;
    }

    @Override
    public String getEventTypeName() {
        return EVENT_TYPE;
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roomId", roomId);
        payload.put("messageId", messageId);
        payload.put("senderId", senderId);
        payload.put("messageType", messageType);
        payload.put("content", content);
        payload.put("createdAt", createdAt == null ? null : createdAt.toString());
        return payload;
    }
}
