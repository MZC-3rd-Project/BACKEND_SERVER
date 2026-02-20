package com.example.chat.service.realtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ChatRealtimeRedisSubscriberTest {

    @Mock
    private ChatWebSocketSessionRegistry sessionRegistry;

    @InjectMocks
    private ChatRealtimeRedisSubscriber chatRealtimeRedisSubscriber;

    @Test
    void onMessage_broadcastsRoomPayload() {
        Message message = new DefaultMessage(
                "chat-room-events".getBytes(StandardCharsets.UTF_8),
                "{\"roomId\":100,\"payload\":\"hello\"}".getBytes(StandardCharsets.UTF_8)
        );

        chatRealtimeRedisSubscriber.onMessage(message, null);

        verify(sessionRegistry).broadcastToRoom(100L, "hello");
    }

    @Test
    void onMessage_ignoresInvalidPayload() {
        Message message = new DefaultMessage(
                "chat-room-events".getBytes(StandardCharsets.UTF_8),
                "not-json".getBytes(StandardCharsets.UTF_8)
        );

        chatRealtimeRedisSubscriber.onMessage(message, null);

        verifyNoInteractions(sessionRegistry);
    }
}
