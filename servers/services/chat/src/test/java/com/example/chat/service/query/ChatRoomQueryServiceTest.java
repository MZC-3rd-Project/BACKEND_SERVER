package com.example.chat.service.query;

import com.example.chat.dto.query.response.ChatMessageItemResponse;
import com.example.chat.dto.query.response.ChatRoomLastMessageResponse;
import com.example.chat.dto.query.response.ChatRoomSummaryResponse;
import com.example.chat.entity.message.ChatMessage;
import com.example.chat.entity.message.ChatMessageType;
import com.example.chat.entity.participant.ChatParticipantRole;
import com.example.chat.entity.room.ChatSalesChannel;
import com.example.chat.entity.participant.ChatRoomParticipant;
import com.example.chat.exception.ChatErrorCode;
import com.example.chat.repository.ChatMessageRepository;
import com.example.chat.repository.ChatRoomParticipantRepository;
import com.example.chat.service.policy.ChatRoomAccessPolicy;
import com.example.core.exception.BusinessException;
import com.example.core.pagination.CursorResponse;
import com.example.core.pagination.CursorUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomQueryServiceTest {

    @Mock
    private ChatRoomParticipantRepository chatRoomParticipantRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatRoomAccessPolicy chatRoomAccessPolicy;

    @Mock
    private ChatRoomSummaryReader chatRoomSummaryReader;

    @Mock
    private ChatMessagePresenter chatMessagePresenter;

    @InjectMocks
    private ChatRoomQueryService chatRoomQueryService;

    @Test
    void findMyRooms_returnsCursorPagedRoomSummaries() {
        ChatRoomParticipant participant1 = ChatRoomParticipant.create(200L, 10L, ChatParticipantRole.PARTICIPANT);
        ChatRoomParticipant participant2 = ChatRoomParticipant.create(100L, 10L, ChatParticipantRole.PARTICIPANT);

        when(chatRoomParticipantRepository.findActiveParticipantsOrderByLatestMessage(10L, "ACTIVE", null, null, 2))
                .thenReturn(List.of(participant1, participant2));
        when(chatRoomSummaryReader.readSummaries(10L, List.of(participant1))).thenReturn(List.of(
            ChatRoomSummaryResponse.builder()
                .roomId(200L)
                .title("문의방")
                .salesChannel(ChatSalesChannel.NORMAL_SALE)
                .unreadCount(5L)
                .lastMessage(ChatRoomLastMessageResponse.builder()
                    .messageId(999L)
                    .preview("hello")
                    .createdAt(LocalDateTime.now())
                    .build())
                .build()
        ));

        CursorResponse<ChatRoomSummaryResponse> response = chatRoomQueryService.findMyRooms(10L, null, 1);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.isHasNext()).isTrue();
        ChatRoomListCursorCodec.Cursor nextCursor = ChatRoomListCursorCodec.decode(response.getNextCursor());
        assertThat(nextCursor.lastMessageId()).isEqualTo(999L);
        assertThat(nextCursor.roomId()).isEqualTo(200L);

        ChatRoomSummaryResponse room = response.getItems().get(0);
        assertThat(room.getRoomId()).isEqualTo(200L);
        assertThat(room.getUnreadCount()).isEqualTo(5L);
        assertThat(room.getLastMessage()).isNotNull();
        assertThat(room.getLastMessage().getMessageId()).isEqualTo(999L);
    }

    @Test
    void findRoomMessages_blocksRefundedParticipant() {
        when(chatRoomAccessPolicy.requireActiveParticipant(100L, 10L))
            .thenThrow(new BusinessException(ChatErrorCode.FORBIDDEN_ROOM_ACCESS));

        assertThatThrownBy(() -> chatRoomQueryService.findRoomMessages(100L, 10L, null, 50))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ChatErrorCode.FORBIDDEN_ROOM_ACCESS);
    }

    @Test
    void findRoomMessages_returnsCursorPagedMessages() {
        ChatRoomParticipant participant = ChatRoomParticipant.create(100L, 10L, ChatParticipantRole.PARTICIPANT);
        when(chatRoomAccessPolicy.requireActiveParticipant(100L, 10L)).thenReturn(participant);

        ChatMessage m1 = ChatMessage.create(100L, 10L, ChatMessageType.CHAT, "c1", "one", "one", null);
        ReflectionTestUtils.setField(m1, "id", 200L);
        ReflectionTestUtils.setField(m1, "createdAt", LocalDateTime.now());

        ChatMessage m2 = ChatMessage.create(100L, 10L, ChatMessageType.CHAT, "c2", "two", "two", null);
        ReflectionTestUtils.setField(m2, "id", 100L);
        ReflectionTestUtils.setField(m2, "createdAt", LocalDateTime.now());

        when(chatMessageRepository.findByRoomIdOrderByIdDesc(eq(100L), any(Pageable.class)))
                .thenReturn(List.of(m1, m2));
        when(chatMessagePresenter.toItemResponse(m1)).thenReturn(ChatMessageItemResponse.builder()
            .messageId(200L)
            .senderId(10L)
            .messageType(ChatMessageType.CHAT)
            .content("one")
            .createdAt(m1.getCreatedAt())
            .build());

        CursorResponse<ChatMessageItemResponse> response = chatRoomQueryService.findRoomMessages(100L, 10L, null, 1);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.isHasNext()).isTrue();
        assertThat(CursorUtils.decodeLong(response.getNextCursor())).isEqualTo(200L);
        assertThat(response.getItems().get(0).getMessageId()).isEqualTo(200L);
    }

    @Test
    void findMessagesAfter_returnsAscendingMissedMessages() {
        ChatRoomParticipant participant = ChatRoomParticipant.create(100L, 10L, ChatParticipantRole.PARTICIPANT);
        when(chatRoomAccessPolicy.requireActiveParticipant(100L, 10L)).thenReturn(participant);

        ChatMessage m1 = ChatMessage.create(100L, 20L, ChatMessageType.CHAT, "c1", "one", "one", null);
        ReflectionTestUtils.setField(m1, "id", 101L);
        ReflectionTestUtils.setField(m1, "createdAt", LocalDateTime.now());

        ChatMessage m2 = ChatMessage.create(100L, 20L, ChatMessageType.CHAT, "c2", "two", "two", null);
        ReflectionTestUtils.setField(m2, "id", 102L);
        ReflectionTestUtils.setField(m2, "createdAt", LocalDateTime.now());

        when(chatMessageRepository.findByRoomIdAndIdGreaterThanOrderByIdAsc(eq(100L), eq(100L), any(Pageable.class)))
                .thenReturn(List.of(m1, m2));
        when(chatMessagePresenter.toItemResponse(m1)).thenReturn(ChatMessageItemResponse.builder()
            .messageId(101L)
            .senderId(20L)
            .messageType(ChatMessageType.CHAT)
            .content("one")
            .createdAt(m1.getCreatedAt())
            .build());
        when(chatMessagePresenter.toItemResponse(m2)).thenReturn(ChatMessageItemResponse.builder()
            .messageId(102L)
            .senderId(20L)
            .messageType(ChatMessageType.CHAT)
            .content("two")
            .createdAt(m2.getCreatedAt())
            .build());

        List<ChatMessageItemResponse> missed = chatRoomQueryService.findMessagesAfter(100L, 10L, 100L, 100);

        assertThat(missed).hasSize(2);
        assertThat(missed).extracting(ChatMessageItemResponse::getMessageId).containsExactly(101L, 102L);
    }
}
