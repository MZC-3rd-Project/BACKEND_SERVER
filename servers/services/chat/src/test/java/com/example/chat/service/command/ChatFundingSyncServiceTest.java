package com.example.chat.service.command;

import com.example.chat.consumer.FundingEventMessage;
import com.example.chat.entity.participant.ChatParticipantRole;
import com.example.chat.entity.participant.ChatParticipantStatus;
import com.example.chat.entity.participant.ChatRoomParticipant;
import com.example.chat.entity.room.ChatRoom;
import com.example.chat.entity.room.ChatRoomReadOnlyReason;
import com.example.chat.entity.room.ChatRoomStatus;
import com.example.chat.repository.ChatRoomParticipantRepository;
import com.example.chat.repository.ChatRoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatFundingSyncServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatRoomParticipantRepository chatRoomParticipantRepository;

    @InjectMocks
    private ChatFundingSyncService chatFundingSyncService;

    @Test
    void syncFundingCreated_createsRoomAndSellerParticipant() {
        FundingEventMessage event = event("evt-1", "FUNDING_CREATED", 10L, 20L, 30L, null);

        when(chatRoomRepository.findByRoomKey("funding:10")).thenReturn(Optional.empty());

        ChatRoom savedRoom = ChatRoom.createFundingGroupRoom("funding:10", 10L, 20L, 30L, "펀딩 캠페인 #10");
        ReflectionTestUtils.setField(savedRoom, "id", 1000L);
        when(chatRoomRepository.save(any(ChatRoom.class))).thenReturn(savedRoom);
        when(chatRoomParticipantRepository.findByRoomIdAndUserId(1000L, 30L)).thenReturn(Optional.empty());

        chatFundingSyncService.syncFundingCreated(event);

        verify(chatRoomRepository).save(any(ChatRoom.class));

        ArgumentCaptor<ChatRoomParticipant> participantCaptor = ArgumentCaptor.forClass(ChatRoomParticipant.class);
        verify(chatRoomParticipantRepository).save(participantCaptor.capture());
        ChatRoomParticipant savedParticipant = participantCaptor.getValue();

        assertThat(savedParticipant.getRoomId()).isEqualTo(1000L);
        assertThat(savedParticipant.getUserId()).isEqualTo(30L);
        assertThat(savedParticipant.getRole()).isEqualTo(ChatParticipantRole.SELLER_ADMIN);
        assertThat(savedParticipant.getStatus()).isEqualTo(ChatParticipantStatus.ACTIVE);
    }

    @Test
    void syncFundingParticipated_reactivatesRefundedParticipant() {
        ChatRoom room = ChatRoom.createFundingGroupRoom("funding:10", 10L, 20L, 30L, "펀딩 캠페인 #10");
        ReflectionTestUtils.setField(room, "id", 1000L);

        ChatRoomParticipant participant = ChatRoomParticipant.create(1000L, 40L, ChatParticipantRole.PARTICIPANT);
        participant.markRefunded();

        FundingEventMessage event = event("evt-2", "FUNDING_PARTICIPATED", 10L, 20L, null, 40L);

        when(chatRoomRepository.findByCampaignId(10L)).thenReturn(Optional.of(room));
        when(chatRoomParticipantRepository.findByRoomIdAndUserId(1000L, 40L)).thenReturn(Optional.of(participant));

        chatFundingSyncService.syncFundingParticipated(event);

        verify(chatRoomParticipantRepository).save(participant);
        assertThat(participant.getStatus()).isEqualTo(ChatParticipantStatus.ACTIVE);
        assertThat(participant.getLeftAt()).isNull();
    }

    @Test
    void syncFundingRefunded_marksParticipantAsRefunded() {
        ChatRoom room = ChatRoom.createFundingGroupRoom("funding:10", 10L, 20L, 30L, "펀딩 캠페인 #10");
        ReflectionTestUtils.setField(room, "id", 1000L);

        ChatRoomParticipant participant = ChatRoomParticipant.create(1000L, 40L, ChatParticipantRole.PARTICIPANT);
        FundingEventMessage event = event("evt-3", "FUNDING_REFUNDED", 10L, 20L, null, 40L);

        when(chatRoomRepository.findByCampaignId(10L)).thenReturn(Optional.of(room));
        when(chatRoomParticipantRepository.findByRoomIdAndUserId(1000L, 40L)).thenReturn(Optional.of(participant));

        chatFundingSyncService.syncFundingRefunded(event);

        verify(chatRoomParticipantRepository).save(participant);
        assertThat(participant.getStatus()).isEqualTo(ChatParticipantStatus.LEFT_REFUNDED);
        assertThat(participant.getLeftAt()).isNotNull();
    }

    @Test
    void syncFundingClosed_marksRoomReadOnly() {
        ChatRoom room = ChatRoom.createFundingGroupRoom("funding:10", 10L, 20L, 30L, "펀딩 캠페인 #10");
        ReflectionTestUtils.setField(room, "id", 1000L);

        FundingEventMessage event = event("evt-4", "FUNDING_FAILED", 10L, 20L, null, null);
        when(chatRoomRepository.findByCampaignId(10L)).thenReturn(Optional.of(room));

        chatFundingSyncService.syncFundingClosed(event);

        verify(chatRoomRepository).save(room);
        assertThat(room.getStatus()).isEqualTo(ChatRoomStatus.READ_ONLY);
        assertThat(room.getReadOnlyReason()).isEqualTo(ChatRoomReadOnlyReason.FUNDING_FAILED);
        assertThat(room.getReadOnlyAt()).isNotNull();
    }

    @Test
    void syncFundingRefunded_ignoresSellerRefundEvent() {
        ChatRoom room = ChatRoom.createFundingGroupRoom("funding:10", 10L, 20L, 30L, "펀딩 캠페인 #10");
        ReflectionTestUtils.setField(room, "id", 1000L);

        FundingEventMessage event = event("evt-5", "FUNDING_REFUNDED", 10L, 20L, null, 30L);
        when(chatRoomRepository.findByCampaignId(10L)).thenReturn(Optional.of(room));

        chatFundingSyncService.syncFundingRefunded(event);

        verify(chatRoomParticipantRepository, never()).save(any(ChatRoomParticipant.class));
    }

    private FundingEventMessage event(String eventId,
                                      String eventType,
                                      Long campaignId,
                                      Long itemId,
                                      Long sellerId,
                                      Long userId) {
        FundingEventMessage event = new FundingEventMessage();
        ReflectionTestUtils.setField(event, "eventId", eventId);
        ReflectionTestUtils.setField(event, "eventType", eventType);
        ReflectionTestUtils.setField(event, "campaignId", campaignId);
        ReflectionTestUtils.setField(event, "itemId", itemId);
        ReflectionTestUtils.setField(event, "sellerId", sellerId);
        ReflectionTestUtils.setField(event, "userId", userId);
        return event;
    }
}
