package com.example.chat.service.policy;

import com.example.chat.entity.participant.ChatParticipantRole;
import com.example.chat.entity.participant.ChatRoomParticipant;
import com.example.chat.exception.ChatErrorCode;
import com.example.chat.repository.ChatRoomParticipantRepository;
import com.example.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomAccessPolicyTest {

    @Mock
    private ChatRoomParticipantRepository chatRoomParticipantRepository;

    @InjectMocks
    private ChatRoomAccessPolicy chatRoomAccessPolicy;

    @Test
    void requireActiveParticipant_returnsParticipantWhenUserCanAccessRoom() {
        ChatRoomParticipant participant = ChatRoomParticipant.create(100L, 10L, ChatParticipantRole.PARTICIPANT);
        when(chatRoomParticipantRepository.findByRoomIdAndUserId(100L, 10L)).thenReturn(Optional.of(participant));

        ChatRoomParticipant result = chatRoomAccessPolicy.requireActiveParticipant(100L, 10L);

        assertThat(result).isSameAs(participant);
    }

    @Test
    void requireActiveParticipant_blocksInactiveParticipant() {
        ChatRoomParticipant participant = ChatRoomParticipant.create(100L, 10L, ChatParticipantRole.PARTICIPANT);
        participant.markRefunded();
        when(chatRoomParticipantRepository.findByRoomIdAndUserId(100L, 10L)).thenReturn(Optional.of(participant));

        assertThatThrownBy(() -> chatRoomAccessPolicy.requireActiveParticipant(100L, 10L))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ChatErrorCode.FORBIDDEN_ROOM_ACCESS);
    }
}
