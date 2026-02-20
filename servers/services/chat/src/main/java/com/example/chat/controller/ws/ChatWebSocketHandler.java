package com.example.chat.controller.ws;

import com.example.chat.config.ChatRealtimeProperties;
import com.example.chat.dto.command.request.ChatReadUpdateRequest;
import com.example.chat.dto.command.request.CreateChatMessageRequest;
import com.example.chat.dto.command.response.ChatMessageSendResponse;
import com.example.chat.dto.command.response.ChatReadUpdateResponse;
import com.example.chat.dto.query.response.ChatMessageItemResponse;
import com.example.chat.service.command.ChatMessageCommandService;
import com.example.chat.service.command.ChatReadCommandService;
import com.example.chat.service.query.ChatRoomQueryService;
import com.example.chat.service.realtime.ChatRoomRealtimePublisher;
import com.example.chat.service.realtime.ChatWebSocketSessionRegistry;
import com.example.core.exception.BusinessException;
import com.example.core.util.JsonUtils;
import com.example.security.context.AuthContext;
import com.example.security.context.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatWebSocketSessionRegistry sessionRegistry;
    private final ChatRoomQueryService chatRoomQueryService;
    private final ChatMessageCommandService chatMessageCommandService;
    private final ChatReadCommandService chatReadCommandService;
    private final ChatRoomRealtimePublisher chatRoomRealtimePublisher;
    private final ChatRealtimeProperties chatRealtimeProperties;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object userIdAttr = session.getAttributes().get(ChatHandshakeInterceptor.ATTR_USER_ID);
        if (!(userIdAttr instanceof Long userId) || userId <= 0) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        sessionRegistry.register(session, userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.unregister(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long userId = sessionRegistry.getUserId(session);
        if (userId == null || userId <= 0) {
            sendError(session, "CHAT-WS-002", "user context is missing");
            return;
        }

        sessionRegistry.touch(session);
        setWebSocketAuthContext(session, userId);
        try {
            ChatInboundFrame frame = JsonUtils.fromJson(message.getPayload(), ChatInboundFrame.class);
            if (frame.getType() == null) {
                sendError(session, "CHAT-WS-001", "frame type is required");
                return;
            }

            switch (frame.getType()) {
                case SUBSCRIBE_ROOM -> handleSubscribe(session, userId, frame);
                case SEND_MESSAGE -> handleSendMessage(session, userId, frame);
                case READ -> handleRead(session, userId, frame);
                case PING -> sendFrame(session, ChatFrameType.PONG, Map.of());
                default -> sendError(session, "CHAT-WS-003", "unsupported frame type");
            }
        } catch (BusinessException e) {
            sendError(session, e.getErrorCode().getCode(), e.getMessage());
        } catch (Exception e) {
            log.warn("WebSocket frame handling failed. sessionId={}", session.getId(), e);
            sendError(session, "CHAT-WS-500", "internal server error");
        } finally {
            AuthContextHolder.clear();
        }
    }

    private void handleSubscribe(WebSocketSession session, Long userId, ChatInboundFrame frame) {
        if (frame.getRoomId() == null || frame.getRoomId() <= 0) {
            sendError(session, "CHAT-WS-004", "roomId is required");
            return;
        }

        chatRoomQueryService.validateRoomAccess(frame.getRoomId(), userId);
        sessionRegistry.subscribeRoom(session, frame.getRoomId());

        sendFrame(session, ChatFrameType.SUBSCRIBED, Map.of(
                "roomId", frame.getRoomId()
        ));

        syncMissedMessages(session, userId, frame);
    }

    private void handleSendMessage(WebSocketSession session, Long userId, ChatInboundFrame frame) {
        if (frame.getRoomId() == null || frame.getRoomId() <= 0) {
            sendError(session, "CHAT-WS-004", "roomId is required");
            return;
        }

        CreateChatMessageRequest request = CreateChatMessageRequest.builder()
                .clientMessageId(frame.getClientMessageId())
                .messageType(frame.getMessageType())
                .content(frame.getContent())
                .metadata(frame.getMetadata())
                .build();

        ChatMessageSendResponse response = chatMessageCommandService.sendMessage(frame.getRoomId(), request, userId);

        Map<String, Object> ackPayload = new LinkedHashMap<>();
        ackPayload.put("clientMessageId", frame.getClientMessageId());
        ackPayload.put("messageId", response.getMessageId());
        ackPayload.put("createdAt", response.getCreatedAt());
        ackPayload.put("duplicated", response.isDuplicated());
        sendFrame(session, ChatFrameType.MESSAGE_ACK, ackPayload);

        if (!response.isDuplicated()) {
            Map<String, Object> roomPayload = new LinkedHashMap<>();
            roomPayload.put("roomId", response.getRoomId());
            roomPayload.put("messageId", response.getMessageId());
            roomPayload.put("senderId", response.getSenderId());
            roomPayload.put("messageType", response.getMessageType());
            roomPayload.put("content", response.getContent());
            roomPayload.put("createdAt", response.getCreatedAt());

            String serialized = JsonUtils.toJson(ChatOutboundFrame.builder()
                    .type(ChatFrameType.ROOM_MESSAGE)
                    .payload(roomPayload)
                    .build());
            chatRoomRealtimePublisher.publishRoomMessage(response.getRoomId(), serialized);
        }
    }

    private void handleRead(WebSocketSession session, Long userId, ChatInboundFrame frame) {
        if (frame.getRoomId() == null || frame.getRoomId() <= 0) {
            sendError(session, "CHAT-WS-004", "roomId is required");
            return;
        }
        if (frame.getLastReadMessageId() == null || frame.getLastReadMessageId() <= 0) {
            sendError(session, "CHAT-WS-005", "lastReadMessageId is required");
            return;
        }

        ChatReadUpdateResponse response = chatReadCommandService.updateReadPointer(
                frame.getRoomId(),
                userId,
                ChatReadUpdateRequest.builder()
                        .lastReadMessageId(frame.getLastReadMessageId())
                        .build()
        );

        sendFrame(session, ChatFrameType.READ_ACK, Map.of(
                "roomId", response.getRoomId(),
                "lastReadMessageId", response.getLastReadMessageId()
        ));
    }

    private void sendError(WebSocketSession session, String code, String message) {
        sendFrame(session, ChatFrameType.ERROR, Map.of(
                "code", code,
                "message", message
        ));
    }

    private void sendFrame(WebSocketSession session, ChatFrameType type, Map<String, Object> payload) {
        String serialized = JsonUtils.toJson(ChatOutboundFrame.builder()
                .type(type)
                .payload(payload)
                .build());
        sessionRegistry.sendToSession(session, serialized);
    }

    private void syncMissedMessages(WebSocketSession session, Long userId, ChatInboundFrame frame) {
        if (frame.getLastReceivedMessageId() == null || frame.getLastReceivedMessageId() <= 0) {
            return;
        }

        int replaySize = Math.max(1, chatRealtimeProperties.getReplayBatchSize());
        List<ChatMessageItemResponse> missedMessages = chatRoomQueryService.findMessagesAfter(
                frame.getRoomId(),
                userId,
                frame.getLastReceivedMessageId(),
                replaySize
        );

        for (ChatMessageItemResponse missed : missedMessages) {
            sendFrame(session, ChatFrameType.ROOM_MESSAGE, Map.of(
                    "roomId", frame.getRoomId(),
                    "messageId", missed.getMessageId(),
                    "senderId", missed.getSenderId(),
                    "messageType", missed.getMessageType(),
                    "content", missed.getContent(),
                    "createdAt", missed.getCreatedAt()
            ));
        }
    }

    @SuppressWarnings("unchecked")
    private void setWebSocketAuthContext(WebSocketSession session, Long userId) {
        Object rolesAttr = session.getAttributes().get(ChatHandshakeInterceptor.ATTR_ROLES);
        List<String> roles = rolesAttr instanceof List<?> raw
                ? raw.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                : List.of();

        AuthContextHolder.setContext(AuthContext.builder()
                .userId(String.valueOf(userId))
                .roles(roles)
                .nonce("ws")
                .timestamp(System.currentTimeMillis())
                .build());
    }
}
