package com.example.chat.service.query;

import com.example.chat.client.ChatProductLookupClient;
import com.example.chat.client.ChatProductSnapshot;
import com.example.chat.client.ChatProfileLookupClient;
import com.example.chat.client.ChatProfileSnapshot;
import com.example.chat.client.ChatStoreLookupClient;
import com.example.chat.client.ChatStoreSnapshot;
import com.example.chat.dto.query.response.ChatRoomSummaryResponse;
import com.example.chat.entity.message.ChatMessage;
import com.example.chat.entity.message.ChatMessageType;
import com.example.chat.entity.participant.ChatParticipantRole;
import com.example.chat.entity.participant.ChatRoomParticipant;
import com.example.chat.entity.room.ChatRoom;
import com.example.chat.entity.room.ChatSalesChannel;
import com.example.chat.repository.ChatRoomParticipantRepository;
import com.example.clients.media.facade.MediaClientFacade;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomSummaryReaderTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatRoomParticipantRepository chatRoomParticipantRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatMessagePresenter chatMessagePresenter;

    @Mock
    private ChatProductLookupClient chatProductLookupClient;

    @Mock
    private ChatStoreLookupClient chatStoreLookupClient;

    @Mock
    private ChatProfileLookupClient chatProfileLookupClient;

    @Mock
    private MediaClientFacade mediaClientFacade;

    @Mock
    private ChatSalesChannelResolver chatSalesChannelResolver;

    @InjectMocks
    private ChatRoomSummaryReader chatRoomSummaryReader;

    @Test
    void readSummaries_combinesRoomUnreadCountAndLastMessage() {
        ChatRoomParticipant sellerParticipant = ChatRoomParticipant.create(200L, 20L, ChatParticipantRole.SELLER_ADMIN);
        sellerParticipant.updateLastReadMessageId(800L);
        ChatRoomParticipant buyerParticipant = ChatRoomParticipant.create(200L, 10L, ChatParticipantRole.PARTICIPANT);

        ChatRoom room = ChatRoom.createInquiryRoom("inquiry:1:10:20", 1L, 20L, "문의방");
        ReflectionTestUtils.setField(room, "id", 200L);
        ReflectionTestUtils.setField(room, "createdAt", LocalDateTime.of(2026, 3, 1, 10, 0));

        ChatMessage lastMessage = ChatMessage.create(200L, 20L, ChatMessageType.CHAT, "c1", "hello", "hello", null);
        ReflectionTestUtils.setField(lastMessage, "id", 999L);
        ReflectionTestUtils.setField(lastMessage, "createdAt", LocalDateTime.of(2026, 3, 18, 12, 0));

        when(chatRoomRepository.findAllById(List.of(200L))).thenReturn(List.of(room));
        when(chatRoomParticipantRepository.findByRoomIdInOrderByRoomIdAscIdAsc(List.of(200L)))
            .thenReturn(List.of(sellerParticipant, buyerParticipant));
        when(chatMessageRepository.countByRoomIdAndIdGreaterThan(200L, 800L)).thenReturn(3L);
        when(chatMessageRepository.findLatestByRoomIdIn(List.of(200L))).thenReturn(List.of(lastMessage));
        when(chatMessagePresenter.toLastMessageResponse(lastMessage)).thenReturn(
            com.example.chat.dto.query.response.ChatRoomLastMessageResponse.builder()
                .messageId(999L)
                .preview("hello")
                .createdAt(lastMessage.getCreatedAt())
                .build()
        );
        when(chatProductLookupClient.findItems(List.of(1L))).thenReturn(Map.of(
            1L, new ChatProductSnapshot(1L, 20L, 300L, "상품 A", "HOT_DEAL", 701L)
        ));
        when(chatStoreLookupClient.findStores(List.of(300L))).thenReturn(Map.of(
            300L, new ChatStoreSnapshot(300L, "스토어 A")
        ));
        when(chatProfileLookupClient.findProfiles(List.of(10L))).thenReturn(Map.of(
            10L, new ChatProfileSnapshot(10L, "구매자A")
        ));
        when(mediaClientFacade.getMediaUrlMap(List.of(701L))).thenReturn(Map.of(701L, "https://cdn.example.com/701.webp"));
        when(chatSalesChannelResolver.resolve(room, new ChatProductSnapshot(1L, 20L, 300L, "상품 A", "HOT_DEAL", 701L)))
            .thenReturn(ChatSalesChannel.HOT_DEAL);

        List<ChatRoomSummaryResponse> result = chatRoomSummaryReader.readSummaries(20L, List.of(sellerParticipant));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getRoomId()).isEqualTo(200L);
        assertThat(result.getFirst().getUnreadCount()).isEqualTo(3L);
        assertThat(result.getFirst().getLastMessage()).isNotNull();
        assertThat(result.getFirst().getLastMessage().getMessageId()).isEqualTo(999L);
        assertThat(result.getFirst().getStoreId()).isEqualTo(300L);
        assertThat(result.getFirst().getStoreName()).isEqualTo("스토어 A");
        assertThat(result.getFirst().getItemThumbnailUrl()).isEqualTo("https://cdn.example.com/701.webp");
        assertThat(result.getFirst().getBuyerDisplayName()).isEqualTo("구매자A");
        assertThat(result.getFirst().getSalesChannel()).isEqualTo(ChatSalesChannel.HOT_DEAL);
        assertThat(result.getFirst().getUpdatedAt()).isEqualTo(lastMessage.getCreatedAt());
    }

    @Test
    void readSummaries_skipsParticipantWhenRoomIsMissing() {
        ChatRoomParticipant participant = ChatRoomParticipant.create(200L, 10L, ChatParticipantRole.PARTICIPANT);
        when(chatRoomRepository.findAllById(List.of(200L))).thenReturn(List.of());

        List<ChatRoomSummaryResponse> result = chatRoomSummaryReader.readSummaries(10L, List.of(participant));

        assertThat(result).isEmpty();
    }
}
