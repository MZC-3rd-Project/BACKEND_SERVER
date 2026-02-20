package com.example.chat.controller.ws;

import com.example.chat.entity.message.ChatMessageType;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
public class ChatInboundFrame {

    private ChatFrameType type;

    private Long roomId;
    private Long lastReceivedMessageId;

    private String clientMessageId;
    private ChatMessageType messageType;
    private String content;
    private Map<String, Object> metadata;

    private Long lastReadMessageId;
}
