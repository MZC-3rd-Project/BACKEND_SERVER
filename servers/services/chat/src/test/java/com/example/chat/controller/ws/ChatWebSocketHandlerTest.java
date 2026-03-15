package com.example.chat.controller.ws;

import com.example.chat.config.ChatRealtimeProperties;
import com.example.chat.exception.ChatErrorCode;
import com.example.chat.service.command.ChatMessageCommandService;
import com.example.chat.service.command.ChatReadCommandService;
import com.example.chat.service.policy.ChatRoomAccessPolicy;
import com.example.chat.service.query.ChatRoomQueryService;
import com.example.chat.service.realtime.ChatPresenceService;
import com.example.chat.service.realtime.ChatRoomRealtimePublisher;
import com.example.chat.service.realtime.ChatWebSocketSessionRegistry;
import com.example.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketHandlerTest {

    @Mock
    private ChatWebSocketSessionRegistry sessionRegistry;

    @Mock
    private ChatRoomAccessPolicy chatRoomAccessPolicy;

    @Mock
    private ChatRoomQueryService chatRoomQueryService;

    @Mock
    private ChatMessageCommandService chatMessageCommandService;

    @Mock
    private ChatReadCommandService chatReadCommandService;

    @Mock
    private ChatRoomRealtimePublisher chatRoomRealtimePublisher;

    @Mock
    private ChatPresenceService chatPresenceService;

    @Mock
    private WebSocketSession session;

    private ChatWebSocketHandler chatWebSocketHandler;

    @BeforeEach
    void setUp() {
        ChatRealtimeProperties chatRealtimeProperties = new ChatRealtimeProperties();
        chatWebSocketHandler = new ChatWebSocketHandler(
            sessionRegistry,
            chatRoomAccessPolicy,
            chatRoomQueryService,
            chatMessageCommandService,
            chatReadCommandService,
            chatRoomRealtimePublisher,
            chatRealtimeProperties,
            chatPresenceService
        );

        when(sessionRegistry.getUserId(session)).thenReturn(10L);
        when(session.getAttributes()).thenReturn(new HashMap<>(java.util.Map.of(
            ChatHandshakeInterceptor.ATTR_ROLES, List.of("ROLE_USER")
        )));
    }

    @Test
    void handleSubscribe_usesRoomAccessPolicyBeforeSubscription() {
        chatWebSocketHandler.handleTextMessage(
            session,
            new TextMessage("{\"type\":\"SUBSCRIBE_ROOM\",\"roomId\":100}")
        );

        verify(chatRoomAccessPolicy).requireActiveParticipant(100L, 10L);
        verify(sessionRegistry).subscribeRoom(session, 100L);
        verify(sessionRegistry).sendToSession(
            eq(session),
            argThat(payload -> payload.contains("\"type\":\"SUBSCRIBED\"") && payload.contains("\"roomId\":100"))
        );
    }

    @Test
    void handleSubscribe_returnsErrorWhenRoomAccessIsForbidden() {
        when(chatRoomAccessPolicy.requireActiveParticipant(100L, 10L))
            .thenThrow(new BusinessException(ChatErrorCode.FORBIDDEN_ROOM_ACCESS));

        chatWebSocketHandler.handleTextMessage(
            session,
            new TextMessage("{\"type\":\"SUBSCRIBE_ROOM\",\"roomId\":100}")
        );

        verify(sessionRegistry, never()).subscribeRoom(session, 100L);
        verify(sessionRegistry).sendToSession(
            eq(session),
            argThat(payload -> payload.contains("\"type\":\"ERROR\"") && payload.contains(ChatErrorCode.FORBIDDEN_ROOM_ACCESS.getCode()))
        );
    }
}
