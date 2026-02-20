package com.example.chat.event;

import com.example.chat.entity.message.ChatMessageType;
import com.example.chat.entity.room.ChatRoomType;
import com.example.event.DomainEvent;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class ChatNotificationRequestedEvent extends DomainEvent {

    private static final String TOPIC = "chat-notification-events";
    private static final String EVENT_TYPE = "CHAT_NOTIFICATION_REQUESTED";

    private final Long recipientId;
    private final Long roomId;
    private final String roomType;
    private final Long messageId;
    private final Long senderId;
    private final String messageType;
    private final String preview;
    private final LocalDateTime createdAt;

    public ChatNotificationRequestedEvent(Long recipientId,
                                          Long roomId,
                                          ChatRoomType roomType,
                                          Long messageId,
                                          Long senderId,
                                          ChatMessageType messageType,
                                          String preview,
                                          LocalDateTime createdAt) {
        super(TOPIC);
        this.recipientId = recipientId;
        this.roomId = roomId;
        this.roomType = roomType == null ? null : roomType.name();
        this.messageId = messageId;
        this.senderId = senderId;
        this.messageType = messageType == null ? null : messageType.name();
        this.preview = preview;
        this.createdAt = createdAt;
    }

    @Override
    public String getEventTypeName() {
        return EVENT_TYPE;
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recipientId", recipientId);
        payload.put("roomId", roomId);
        payload.put("roomType", roomType);
        payload.put("messageId", messageId);
        payload.put("senderId", senderId);
        payload.put("messageType", messageType);
        payload.put("preview", preview);
        payload.put("createdAt", createdAt == null ? null : createdAt.toString());
        return payload;
    }
}
