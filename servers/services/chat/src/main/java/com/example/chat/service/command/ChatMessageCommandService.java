package com.example.chat.service.command;

import com.example.chat.dto.command.request.CreateChatMessageRequest;
import com.example.chat.dto.command.response.ChatMessageSendResponse;
import com.example.chat.entity.message.ChatMessage;
import com.example.chat.entity.message.ChatMessageType;
import com.example.chat.entity.audit.ChatAuditEventType;
import com.example.chat.entity.participant.ChatParticipantStatus;
import com.example.chat.entity.participant.ChatRoomParticipant;
import com.example.chat.entity.room.ChatRoom;
import com.example.chat.event.ChatMessageCreatedEvent;
import com.example.chat.event.ChatNotificationRequestedEvent;
import com.example.chat.exception.ChatErrorCode;
import com.example.chat.repository.ChatMessageRepository;
import com.example.chat.repository.ChatRoomParticipantRepository;
import com.example.chat.repository.ChatRoomRepository;
import com.example.chat.service.audit.ChatAuditService;
import com.example.chat.service.content.ChatContentSanitizer;
import com.example.chat.service.policy.ChatMessagePolicyService;
import com.example.chat.service.policy.ChatMessageRateLimitService;
import com.example.chat.service.realtime.ChatPresenceService;
import com.example.core.exception.BusinessException;
import com.example.core.util.JsonUtils;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatMessageCommandService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatContentSanitizer chatContentSanitizer;
    private final ChatMessagePolicyService chatMessagePolicyService;
    private final ChatMessageRateLimitService chatMessageRateLimitService;
    private final ChatPresenceService chatPresenceService;
    private final ChatAuditService chatAuditService;
    private final EventPublisher eventPublisher;

    @Transactional
    public ChatMessageSendResponse sendMessage(Long roomId, CreateChatMessageRequest request, Long senderId) {
        if (roomId == null || roomId <= 0 || request == null || senderId == null || senderId <= 0) {
            throw new BusinessException(ChatErrorCode.INVALID_MESSAGE_CONTENT);
        }

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.ROOM_NOT_FOUND));

        ChatRoomParticipant participant = chatRoomParticipantRepository.findByRoomIdAndUserId(roomId, senderId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.FORBIDDEN_ROOM_ACCESS));

        ChatMessageType messageType = request.getMessageType();
        if (messageType == null) {
            throw new BusinessException(ChatErrorCode.INVALID_MESSAGE_TYPE);
        }

        String sanitizedContent = chatContentSanitizer.sanitize(request.getContent());
        if (!StringUtils.hasText(sanitizedContent)) {
            throw new BusinessException(ChatErrorCode.INVALID_MESSAGE_CONTENT);
        }

        chatMessagePolicyService.validateSendPermission(room, participant, senderId, messageType);

        String clientMessageId = StringUtils.hasText(request.getClientMessageId())
                ? request.getClientMessageId()
                : null;

        if (clientMessageId != null) {
            ChatMessage existing = chatMessageRepository.findByRoomIdAndSenderIdAndClientMessageId(
                    roomId,
                    senderId,
                    clientMessageId
            ).orElse(null);
            if (existing != null) {
                return toResponse(existing, true);
            }
        }

        chatMessageRateLimitService.validateMessageSendRate(senderId);

        String metadata = request.getMetadata() == null ? null : JsonUtils.toJson(request.getMetadata());
        ChatMessage created;
        try {
            created = chatMessageRepository.save(ChatMessage.create(
                    roomId,
                    senderId,
                    messageType,
                    clientMessageId,
                    request.getContent(),
                    sanitizedContent,
                    metadata
            ));
        } catch (DataIntegrityViolationException e) {
            if (clientMessageId == null) {
                throw e;
            }
            ChatMessage existing = chatMessageRepository.findByRoomIdAndSenderIdAndClientMessageId(
                    roomId,
                    senderId,
                    clientMessageId
            ).orElseThrow(() -> e);
            return toResponse(existing, true);
        }

        publishMessageCreatedEvent(created);
        publishOfflineNotificationEvents(room, created);
        auditMessageSent(room, created);
        return toResponse(created, false);
    }

    private ChatMessageSendResponse toResponse(ChatMessage message, boolean duplicated) {
        return ChatMessageSendResponse.builder()
                .messageId(message.getId())
                .roomId(message.getRoomId())
                .senderId(message.getSenderId())
                .messageType(message.getMessageType())
                .content(message.getContentSanitized())
                .createdAt(message.getCreatedAt())
                .duplicated(duplicated)
                .build();
    }

    private void publishMessageCreatedEvent(ChatMessage message) {
        eventPublisher.publish(
                new ChatMessageCreatedEvent(
                        message.getRoomId(),
                        message.getId(),
                        message.getSenderId(),
                        message.getMessageType(),
                        message.getContentSanitized(),
                        message.getCreatedAt()
                ),
                EventMetadata.of("ChatRoom", String.valueOf(message.getRoomId()))
        );
    }

    private void publishOfflineNotificationEvents(ChatRoom room, ChatMessage message) {
        String preview = buildPreview(message.getContentSanitized());

        for (ChatRoomParticipant recipient : chatRoomParticipantRepository.findByRoomIdAndStatusOrderByIdAsc(
                room.getId(),
                ChatParticipantStatus.ACTIVE
        )) {
            if (recipient.getUserId().equals(message.getSenderId())) {
                continue;
            }
            if (chatPresenceService.isOnline(recipient.getUserId())) {
                continue;
            }

            eventPublisher.publish(
                    new ChatNotificationRequestedEvent(
                            recipient.getUserId(),
                            room.getId(),
                            room.getRoomType(),
                            message.getId(),
                            message.getSenderId(),
                            message.getMessageType(),
                            preview,
                            message.getCreatedAt()
                    ),
                    EventMetadata.of("ChatRoom", String.valueOf(room.getId()))
            );
        }
    }

    private String buildPreview(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        if (content.length() <= 100) {
            return content;
        }
        return content.substring(0, 100);
    }

    private void auditMessageSent(ChatRoom room, ChatMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", message.getId());
        payload.put("senderId", message.getSenderId());
        payload.put("messageType", message.getMessageType());
        payload.put("preview", buildPreview(message.getContentSanitized()));

        chatAuditService.logEvent(
                message.getSenderId(),
                room.getId(),
                null,
                ChatAuditEventType.MESSAGE_SENT,
                payload
        );

        if (message.getMessageType() == ChatMessageType.NOTICE) {
            chatAuditService.logEvent(
                    message.getSenderId(),
                    room.getId(),
                    null,
                    ChatAuditEventType.NOTICE_SENT,
                    payload
            );
        }
    }
}
