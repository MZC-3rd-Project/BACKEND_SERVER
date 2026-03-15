package com.example.chat.service.policy;

import com.example.chat.entity.participant.ChatRoomParticipant;
import com.example.chat.exception.ChatErrorCode;
import com.example.chat.repository.ChatRoomParticipantRepository;
import com.example.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomAccessPolicy {

    private final ChatRoomParticipantRepository chatRoomParticipantRepository;

    public ChatRoomParticipant requireActiveParticipant(Long roomId, Long userId) {
        if (roomId == null || roomId <= 0 || userId == null || userId <= 0) {
            throw forbidden();
        }

        ChatRoomParticipant participant = chatRoomParticipantRepository.findByRoomIdAndUserId(roomId, userId)
            .orElseThrow(this::forbidden);

        if (!participant.isActive()) {
            throw forbidden();
        }

        return participant;
    }

    private BusinessException forbidden() {
        return new BusinessException(ChatErrorCode.FORBIDDEN_ROOM_ACCESS);
    }
}
