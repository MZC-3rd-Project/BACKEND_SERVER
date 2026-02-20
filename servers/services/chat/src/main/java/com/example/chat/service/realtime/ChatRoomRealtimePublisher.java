package com.example.chat.service.realtime;

import com.example.core.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomRealtimePublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final ChannelTopic chatRoomRealtimeTopic;
    private final ChatWebSocketSessionRegistry sessionRegistry;

    public void publishRoomMessage(Long roomId, String payload) {
        if (roomId == null || payload == null) {
            return;
        }

        ChatRoomRealtimeEvent event = new ChatRoomRealtimeEvent(roomId, payload);
        try {
            stringRedisTemplate.convertAndSend(chatRoomRealtimeTopic.getTopic(), JsonUtils.toJson(event));
        } catch (Exception e) {
            log.warn("Redis fan-out failed. fallback local broadcast. roomId={}", roomId, e);
            sessionRegistry.broadcastToRoom(roomId, payload);
        }
    }
}
