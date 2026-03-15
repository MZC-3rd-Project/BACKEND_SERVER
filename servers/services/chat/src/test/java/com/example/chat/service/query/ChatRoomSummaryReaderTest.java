package com.example.chat.service.query;

import com.example.chat.dto.query.response.ChatRoomSummaryResponse;
import com.example.chat.entity.message.ChatMessage;
import com.example.chat.entity.message.ChatMessageType;
import com.example.chat.entity.participant.ChatParticipantRole;
import com.example.chat.entity.participant.ChatRoomParticipant;
import com.example.chat.entity.room.ChatRoom;
import com.example.chat.repository.ChatMessageRepository;
import com.example.chat.repository.ChatRoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomSummaryReaderTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatMessagePresenter chatMessagePresenter;

    @InjectMocks
    private ChatRoomSummaryReader chatRoomSummaryReader;

    @Test
    void readSummaries_combinesRoomUnreadCountAndLastMessage() {
        ChatRoomParticipant participant = ChatRoomParticipant.create(200L, 10L, ChatParticipantRole.PARTICIPANT);
        participant.updateLastReadMessageId(800L);

        ChatRoom room = ChatRoom.createInquiryRoom("inquiry:1:10:20", 1L, 20L, "문의방");
        ReflectionTestUtils.setField(room, "id", 200L);

        ChatMessage lastMessage = ChatMessage.create(200L, 20L, ChatMessageType.CHAT, "c1", "hello", "hello", null);
        ReflectionTestUtils.setField(lastMessage, "id", 999L);
        ReflectionTestUtils.setField(lastMessage, "createdAt", LocalDateTime.now());

        when(chatRoomRepository.findAllById(List.of(200L))).thenReturn(List.of(room));
        when(chatMessageRepository.countByRoomIdAndIdGreaterThan(200L, 800L)).thenReturn(3L);
        when(chatMessageRepository.findByRoomIdOrderByIdDesc(eq(200L), any(Pageable.class))).thenReturn(List.of(lastMessage));
        when(chatMessagePresenter.toLastMessageResponse(lastMessage)).thenReturn(
            com.example.chat.dto.query.response.ChatRoomLastMessageResponse.builder()
                .messageId(999L)
                .preview("hello")
                .createdAt(lastMessage.getCreatedAt())
                .build()
        );

        List<ChatRoomSummaryResponse> result = chatRoomSummaryReader.readSummaries(List.of(participant));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getRoomId()).isEqualTo(200L);
        assertThat(result.getFirst().getUnreadCount()).isEqualTo(3L);
        assertThat(result.getFirst().getLastMessage()).isNotNull();
        assertThat(result.getFirst().getLastMessage().getMessageId()).isEqualTo(999L);
    }

    @Test
    void readSummaries_skipsParticipantWhenRoomIsMissing() {
        ChatRoomParticipant participant = ChatRoomParticipant.create(200L, 10L, ChatParticipantRole.PARTICIPANT);
        when(chatRoomRepository.findAllById(List.of(200L))).thenReturn(List.of());

        List<ChatRoomSummaryResponse> result = chatRoomSummaryReader.readSummaries(List.of(participant));

        assertThat(result).isEmpty();
    }
}
