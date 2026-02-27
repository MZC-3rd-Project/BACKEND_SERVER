package com.example.chat.service.realtime;

import com.example.core.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRealtimeRedisSubscriber implements MessageListener {

    private final ChatWebSocketSessionRegistry sessionRegistry;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String raw = new String(message.getBody(), StandardCharsets.UTF_8);
            ChatRoomRealtimeEvent event = JsonUtils.fromJson(raw, ChatRoomRealtimeEvent.class);
            if (event == null || event.getRoomId() == null || event.getPayload() == null) {
                return;
            }
            sessionRegistry.broadcastToRoom(event.getRoomId(), event.getPayload());
        } catch (Exception e) {
            log.warn("Failed to consume chat redis event", e);
        }
    }
}
