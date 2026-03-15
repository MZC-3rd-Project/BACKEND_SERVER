package com.example.chat.service.command;

import com.example.chat.dto.command.request.ChatReadUpdateRequest;
import com.example.chat.dto.command.response.ChatReadUpdateResponse;
import com.example.chat.entity.participant.ChatParticipantRole;
import com.example.chat.entity.participant.ChatRoomParticipant;
import com.example.chat.exception.ChatErrorCode;
import com.example.chat.repository.ChatMessageRepository;
import com.example.chat.repository.ChatRoomParticipantRepository;
import com.example.chat.service.policy.ChatRoomAccessPolicy;
import com.example.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatReadCommandServiceTest {

    @Mock
    private ChatRoomParticipantRepository chatRoomParticipantRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatRoomAccessPolicy chatRoomAccessPolicy;

    @InjectMocks
    private ChatReadCommandService chatReadCommandService;

    @Test
    void updateReadPointer_updatesPointerWhenMessageExists() {
        ChatRoomParticipant participant = ChatRoomParticipant.create(100L, 10L, ChatParticipantRole.PARTICIPANT);
        when(chatRoomAccessPolicy.requireActiveParticipant(100L, 10L)).thenReturn(participant);
        when(chatMessageRepository.existsByRoomIdAndId(100L, 500L)).thenReturn(true);

        ChatReadUpdateResponse response = chatReadCommandService.updateReadPointer(
                100L,
                10L,
                ChatReadUpdateRequest.builder().lastReadMessageId(500L).build()
        );

        assertThat(response.getRoomId()).isEqualTo(100L);
        assertThat(response.getLastReadMessageId()).isEqualTo(500L);
        assertThat(participant.getLastReadMessageId()).isEqualTo(500L);
        verify(chatRoomParticipantRepository).save(participant);
    }

    @Test
    void updateReadPointer_blocksRefundedParticipant() {
        when(chatRoomAccessPolicy.requireActiveParticipant(100L, 10L))
            .thenThrow(new BusinessException(ChatErrorCode.FORBIDDEN_ROOM_ACCESS));

        assertThatThrownBy(() -> chatReadCommandService.updateReadPointer(
                100L,
                10L,
                ChatReadUpdateRequest.builder().lastReadMessageId(500L).build()
        )).isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ChatErrorCode.FORBIDDEN_ROOM_ACCESS);
    }

    @Test
    void updateReadPointer_throwsWhenMessageNotFound() {
        ChatRoomParticipant participant = ChatRoomParticipant.create(100L, 10L, ChatParticipantRole.PARTICIPANT);
        when(chatRoomAccessPolicy.requireActiveParticipant(100L, 10L)).thenReturn(participant);
        when(chatMessageRepository.existsByRoomIdAndId(100L, 500L)).thenReturn(false);

        assertThatThrownBy(() -> chatReadCommandService.updateReadPointer(
                100L,
                10L,
                ChatReadUpdateRequest.builder().lastReadMessageId(500L).build()
        )).isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ChatErrorCode.MESSAGE_NOT_FOUND);
    }
}
