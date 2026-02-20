package com.example.chat.service.command;

import com.example.chat.client.ProductClient;
import com.example.chat.dto.command.request.CreateInquiryRoomRequest;
import com.example.chat.dto.command.response.ChatRoomCreateResponse;
import com.example.chat.entity.participant.ChatParticipantRole;
import com.example.chat.entity.participant.ChatRoomParticipant;
import com.example.chat.entity.room.ChatRoom;
import com.example.chat.exception.ChatErrorCode;
import com.example.chat.repository.ChatRoomParticipantRepository;
import com.example.chat.repository.ChatRoomRepository;
import com.example.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomCommandServiceTest {

    @Mock
    private ProductClient productClient;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatRoomParticipantRepository chatRoomParticipantRepository;

    @InjectMocks
    private ChatRoomCommandService chatRoomCommandService;

    @Test
    void createInquiryRoom_createsNewRoomWhenNotExists() {
        CreateInquiryRoomRequest request = CreateInquiryRoomRequest.builder()
                .itemId(100L)
                .build();

        when(productClient.findItemSummary(100L))
                .thenReturn(new ProductClient.ProductItemSummary(100L, 300L, "상품 A"));
        when(chatRoomRepository.findByRoomKey("inquiry:100:200:300"))
                .thenReturn(Optional.empty());

        ChatRoom savedRoom = ChatRoom.createInquiryRoom("inquiry:100:200:300", 100L, 300L, "상품 A");
        ReflectionTestUtils.setField(savedRoom, "id", 1000L);
        when(chatRoomRepository.save(any(ChatRoom.class))).thenReturn(savedRoom);

        ChatRoomParticipant buyer = ChatRoomParticipant.create(1000L, 200L, ChatParticipantRole.PARTICIPANT);
        ChatRoomParticipant seller = ChatRoomParticipant.create(1000L, 300L, ChatParticipantRole.SELLER_ADMIN);
        when(chatRoomParticipantRepository.findByRoomIdOrderByIdAsc(1000L))
                .thenReturn(List.of(buyer, seller));

        ChatRoomCreateResponse response = chatRoomCommandService.createInquiryRoom(request, 200L);

        assertThat(response.getRoomId()).isEqualTo(1000L);
        assertThat(response.getItemId()).isEqualTo(100L);
        assertThat(response.getParticipants()).hasSize(2);
        assertThat(response.getParticipants()).extracting(ChatRoomCreateResponse.ParticipantSummary::getUserId)
                .containsExactly(200L, 300L);

        verify(chatRoomRepository).save(any(ChatRoom.class));
        verify(chatRoomParticipantRepository, times(2)).save(any(ChatRoomParticipant.class));
    }

    @Test
    void createInquiryRoom_returnsExistingRoomWhenAlreadyCreated() {
        CreateInquiryRoomRequest request = CreateInquiryRoomRequest.builder()
                .itemId(100L)
                .build();

        when(productClient.findItemSummary(100L))
                .thenReturn(new ProductClient.ProductItemSummary(100L, 300L, "상품 A"));

        ChatRoom existing = ChatRoom.createInquiryRoom("inquiry:100:200:300", 100L, 300L, "상품 A");
        ReflectionTestUtils.setField(existing, "id", 2000L);
        when(chatRoomRepository.findByRoomKey("inquiry:100:200:300"))
                .thenReturn(Optional.of(existing));

        ChatRoomParticipant buyer = ChatRoomParticipant.create(2000L, 200L, ChatParticipantRole.PARTICIPANT);
        ChatRoomParticipant seller = ChatRoomParticipant.create(2000L, 300L, ChatParticipantRole.SELLER_ADMIN);
        when(chatRoomParticipantRepository.findByRoomIdOrderByIdAsc(2000L))
                .thenReturn(List.of(buyer, seller));

        ChatRoomCreateResponse response = chatRoomCommandService.createInquiryRoom(request, 200L);

        assertThat(response.getRoomId()).isEqualTo(2000L);
        assertThat(response.getParticipants()).hasSize(2);
        verify(chatRoomRepository, never()).save(any(ChatRoom.class));
    }

    @Test
    void createInquiryRoom_throwsWhenProductLookupFails() {
        CreateInquiryRoomRequest request = CreateInquiryRoomRequest.builder()
                .itemId(100L)
                .build();

        when(productClient.findItemSummary(100L)).thenReturn(null);

        assertThatThrownBy(() -> chatRoomCommandService.createInquiryRoom(request, 200L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ChatErrorCode.PRODUCT_SERVICE_ERROR);
    }
}
