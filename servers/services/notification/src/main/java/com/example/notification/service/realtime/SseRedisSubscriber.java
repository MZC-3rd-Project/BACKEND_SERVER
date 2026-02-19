package com.example.notification.service.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class SseRedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final SseConnectionManager sseConnectionManager;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            SseNotificationEvent event = objectMapper.readValue(payload, SseNotificationEvent.class);
            sseConnectionManager.publishToUser(event);
        } catch (Exception e) {
            log.warn("Failed to consume SSE redis message", e);
        }
    }
}
