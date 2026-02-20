package com.example.chat.service.command;

import com.example.chat.dto.command.request.CreateChatMessageRequest;
import com.example.chat.dto.command.response.ChatMessageSendResponse;
import com.example.chat.entity.message.ChatMessage;
import com.example.chat.entity.message.ChatMessageType;
import com.example.chat.entity.participant.ChatParticipantRole;
import com.example.chat.entity.participant.ChatRoomParticipant;
import com.example.chat.entity.room.ChatRoom;
import com.example.chat.repository.ChatMessageRepository;
import com.example.chat.repository.ChatRoomParticipantRepository;
import com.example.chat.repository.ChatRoomRepository;
import com.example.chat.service.content.ChatContentSanitizer;
import com.example.chat.service.policy.ChatMessagePolicyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageCommandServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatRoomParticipantRepository chatRoomParticipantRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatContentSanitizer chatContentSanitizer;

    @Mock
    private ChatMessagePolicyService chatMessagePolicyService;

    @InjectMocks
    private ChatMessageCommandService chatMessageCommandService;

    @Test
    void sendMessage_returnsDuplicatedResponseWhenClientMessageIdExists() {
        CreateChatMessageRequest request = CreateChatMessageRequest.builder()
                .clientMessageId("c-1")
                .messageType(ChatMessageType.CHAT)
                .content("hello")
                .build();

        ChatRoom room = ChatRoom.createInquiryRoom("inquiry:1:2:3", 1L, 3L, "문의방");
        ReflectionTestUtils.setField(room, "id", 100L);

        ChatRoomParticipant participant = ChatRoomParticipant.create(100L, 2L, ChatParticipantRole.PARTICIPANT);

        ChatMessage existing = ChatMessage.create(100L, 2L, ChatMessageType.CHAT, "c-1", "hello", "hello", null);
        ReflectionTestUtils.setField(existing, "id", 999L);
        ReflectionTestUtils.setField(existing, "createdAt", LocalDateTime.now());

        when(chatRoomRepository.findById(100L)).thenReturn(Optional.of(room));
        when(chatRoomParticipantRepository.findByRoomIdAndUserId(100L, 2L)).thenReturn(Optional.of(participant));
        when(chatContentSanitizer.sanitize("hello")).thenReturn("hello");
        when(chatMessageRepository.findByRoomIdAndSenderIdAndClientMessageId(100L, 2L, "c-1"))
                .thenReturn(Optional.of(existing));

        ChatMessageSendResponse response = chatMessageCommandService.sendMessage(100L, request, 2L);

        assertThat(response.isDuplicated()).isTrue();
        assertThat(response.getMessageId()).isEqualTo(999L);
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void sendMessage_savesNewMessageWhenNotDuplicated() {
        CreateChatMessageRequest request = CreateChatMessageRequest.builder()
                .clientMessageId("c-2")
                .messageType(ChatMessageType.CHAT)
                .content(" hello ")
                .metadata(Map.of("k", "v"))
                .build();

        ChatRoom room = ChatRoom.createInquiryRoom("inquiry:1:2:3", 1L, 3L, "문의방");
        ReflectionTestUtils.setField(room, "id", 100L);

        ChatRoomParticipant participant = ChatRoomParticipant.create(100L, 2L, ChatParticipantRole.PARTICIPANT);

        ChatMessage saved = ChatMessage.create(100L, 2L, ChatMessageType.CHAT, "c-2", " hello ", "hello", "{\"k\":\"v\"}");
        ReflectionTestUtils.setField(saved, "id", 1000L);
        ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.now());

        when(chatRoomRepository.findById(100L)).thenReturn(Optional.of(room));
        when(chatRoomParticipantRepository.findByRoomIdAndUserId(100L, 2L)).thenReturn(Optional.of(participant));
        when(chatContentSanitizer.sanitize(" hello ")).thenReturn("hello");
        when(chatMessageRepository.findByRoomIdAndSenderIdAndClientMessageId(100L, 2L, "c-2"))
                .thenReturn(Optional.empty());
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(saved);

        ChatMessageSendResponse response = chatMessageCommandService.sendMessage(100L, request, 2L);

        assertThat(response.isDuplicated()).isFalse();
        assertThat(response.getMessageId()).isEqualTo(1000L);
        assertThat(response.getContent()).isEqualTo("hello");
        verify(chatMessageRepository).save(any(ChatMessage.class));
    }
}
