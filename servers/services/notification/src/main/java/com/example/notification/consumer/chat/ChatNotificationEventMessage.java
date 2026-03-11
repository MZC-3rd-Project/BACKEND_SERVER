package com.example.notification.consumer.chat;

import com.example.event.consumer.EventEnvelope;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatNotificationEventMessage implements EventEnvelope {

    private String eventId;
    private String eventType;

    private Long recipientId;
    private Long roomId;
    private String roomType;
    private Long messageId;
    private Long senderId;
    private String messageType;
    private String preview;
    private String createdAt;
}
