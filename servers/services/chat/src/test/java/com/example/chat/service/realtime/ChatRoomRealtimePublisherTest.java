package com.example.chat.service.realtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomRealtimePublisherTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ChannelTopic chatRoomRealtimeTopic;

    @Mock
    private ChatWebSocketSessionRegistry sessionRegistry;

    @InjectMocks
    private ChatRoomRealtimePublisher chatRoomRealtimePublisher;

    @Test
    void publishRoomMessage_publishesRedisEvent() {
        when(chatRoomRealtimeTopic.getTopic()).thenReturn("chat-room-events");

        chatRoomRealtimePublisher.publishRoomMessage(101L, "payload");

        verify(stringRedisTemplate).convertAndSend(
                eq("chat-room-events"),
                argThat((String value) -> value != null
                        && value.contains("\"roomId\":101")
                        && value.contains("\"payload\":\"payload\""))
        );
    }

    @Test
    void publishRoomMessage_fallbacksToLocalBroadcastWhenRedisFails() {
        when(chatRoomRealtimeTopic.getTopic()).thenReturn("chat-room-events");
        doThrow(new RuntimeException("redis down"))
                .when(stringRedisTemplate)
                .convertAndSend(eq("chat-room-events"), org.mockito.ArgumentMatchers.anyString());

        chatRoomRealtimePublisher.publishRoomMessage(101L, "payload");

        verify(sessionRegistry).broadcastToRoom(101L, "payload");
    }
}
