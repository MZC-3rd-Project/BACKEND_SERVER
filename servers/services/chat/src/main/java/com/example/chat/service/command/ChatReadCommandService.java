package com.example.chat.service.command;

import com.example.chat.dto.command.request.ChatReadUpdateRequest;
import com.example.chat.dto.command.response.ChatReadUpdateResponse;
import com.example.chat.entity.participant.ChatRoomParticipant;
import com.example.chat.exception.ChatErrorCode;
import com.example.chat.repository.ChatMessageRepository;
import com.example.chat.repository.ChatRoomParticipantRepository;
import com.example.chat.service.policy.ChatRoomAccessPolicy;
import com.example.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatReadCommandService {

    private final ChatRoomAccessPolicy chatRoomAccessPolicy;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public ChatReadUpdateResponse updateReadPointer(Long roomId, Long userId, ChatReadUpdateRequest request) {
        if (roomId == null || roomId <= 0 || userId == null || userId <= 0
                || request == null || request.getLastReadMessageId() == null || request.getLastReadMessageId() <= 0) {
            throw new BusinessException(ChatErrorCode.INVALID_MESSAGE_CONTENT);
        }

        ChatRoomParticipant participant = chatRoomAccessPolicy.requireActiveParticipant(roomId, userId);

        Long targetMessageId = request.getLastReadMessageId();
        if (!chatMessageRepository.existsByRoomIdAndId(roomId, targetMessageId)) {
            throw new BusinessException(ChatErrorCode.MESSAGE_NOT_FOUND);
        }

        if (participant.getLastReadMessageId() == null || targetMessageId > participant.getLastReadMessageId()) {
            participant.updateLastReadMessageId(targetMessageId);
            chatRoomParticipantRepository.save(participant);
        }

        return ChatReadUpdateResponse.builder()
                .roomId(roomId)
                .lastReadMessageId(participant.getLastReadMessageId())
                .build();
    }
}
