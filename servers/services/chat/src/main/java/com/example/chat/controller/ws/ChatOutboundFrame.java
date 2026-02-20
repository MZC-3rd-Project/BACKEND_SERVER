package com.example.chat.controller.ws;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class ChatOutboundFrame {

    private ChatFrameType type;
    private Map<String, Object> payload;
}
