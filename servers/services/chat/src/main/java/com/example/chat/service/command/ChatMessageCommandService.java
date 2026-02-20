package com.example.chat.service.command;

import com.example.chat.dto.command.request.CreateChatMessageRequest;
import com.example.chat.dto.command.response.ChatMessageSendResponse;
import com.example.chat.entity.message.ChatMessage;
import com.example.chat.entity.message.ChatMessageType;
import com.example.chat.entity.participant.ChatRoomParticipant;
import com.example.chat.entity.room.ChatRoom;
import com.example.chat.exception.ChatErrorCode;
import com.example.chat.repository.ChatMessageRepository;
import com.example.chat.repository.ChatRoomParticipantRepository;
import com.example.chat.repository.ChatRoomRepository;
import com.example.chat.service.content.ChatContentSanitizer;
import com.example.chat.service.policy.ChatMessagePolicyService;
import com.example.core.exception.BusinessException;
import com.example.core.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ChatMessageCommandService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatContentSanitizer chatContentSanitizer;
    private final ChatMessagePolicyService chatMessagePolicyService;

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

        if (StringUtils.hasText(request.getClientMessageId())) {
            ChatMessage existing = chatMessageRepository.findByRoomIdAndSenderIdAndClientMessageId(
                    roomId,
                    senderId,
                    request.getClientMessageId()
            ).orElse(null);
            if (existing != null) {
                return toResponse(existing, true);
            }
        }

        String metadata = request.getMetadata() == null ? null : JsonUtils.toJson(request.getMetadata());

        ChatMessage created = chatMessageRepository.save(ChatMessage.create(
                roomId,
                senderId,
                messageType,
                request.getClientMessageId(),
                request.getContent(),
                sanitizedContent,
                metadata
        ));

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
}
