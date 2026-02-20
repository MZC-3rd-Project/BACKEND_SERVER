package com.example.chat.service.realtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketSessionRegistryTest {

    @Mock
    private WebSocketSession session1;

    @Mock
    private WebSocketSession session2;

    @Test
    void registerAndUnregister_manageSubscriptions() {
        ChatWebSocketSessionRegistry registry = new ChatWebSocketSessionRegistry();

        when(session1.getId()).thenReturn("s1");

        registry.register(session1, 10L);
        registry.subscribeRoom(session1, 100L);

        assertThat(registry.getUserId(session1)).isEqualTo(10L);
        assertThat(registry.getSubscribedRooms(session1)).containsExactly(100L);

        registry.unregister(session1);

        assertThat(registry.getUserId(session1)).isNull();
        assertThat(registry.getSubscribedRooms(session1)).isEqualTo(Set.of());
    }

    @Test
    void broadcastToRoom_sendsOnlyToSubscribers() throws Exception {
        ChatWebSocketSessionRegistry registry = new ChatWebSocketSessionRegistry();

        when(session1.getId()).thenReturn("s1");
        when(session2.getId()).thenReturn("s2");
        when(session1.isOpen()).thenReturn(true);

        registry.register(session1, 10L);
        registry.register(session2, 20L);
        registry.subscribeRoom(session1, 100L);

        registry.broadcastToRoom(100L, "payload");

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session1).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload()).isEqualTo("payload");

        verify(session2, never()).sendMessage(any(TextMessage.class));
    }
}
