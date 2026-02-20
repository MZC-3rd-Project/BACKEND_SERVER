package com.example.chat.service.command;

import com.example.chat.dto.command.request.CreateChatMessageRequest;
import com.example.chat.dto.command.response.ChatMessageSendResponse;
import com.example.chat.entity.message.ChatMessageType;
import com.example.chat.entity.participant.ChatParticipantRole;
import com.example.chat.entity.participant.ChatRoomParticipant;
import com.example.chat.entity.room.ChatRoom;
import com.example.chat.repository.ChatMessageRepository;
import com.example.chat.repository.ChatRoomParticipantRepository;
import com.example.chat.repository.ChatRoomRepository;
import com.example.event.outbox.OutboxMessage;
import com.example.event.outbox.OutboxRepository;
import com.example.event.outbox.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.outbox.enabled=true",
        "app.outbox.immediate-publish-enabled=false",
        "app.outbox.relay.enabled=false",
        "app.outbox.cleanup.enabled=false"
})
@ActiveProfiles("test")
class ChatMessageCommandOutboxIntegrationTest {

    @Autowired
    private ChatMessageCommandService chatMessageCommandService;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatRoomParticipantRepository chatRoomParticipantRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @BeforeEach
    void setUp() {
        chatMessageRepository.deleteAll();
        chatRoomParticipantRepository.deleteAll();
        chatRoomRepository.deleteAll();
        outboxRepository.deleteAll();
    }

    @Test
    void sendMessage_persistsMessageAndOutboxTogether() {
        ChatRoom room = chatRoomRepository.save(
                ChatRoom.createInquiryRoom("inquiry:100:200:300", 100L, 300L, "상품 문의")
        );
        chatRoomParticipantRepository.save(
                ChatRoomParticipant.create(room.getId(), 200L, ChatParticipantRole.PARTICIPANT)
        );

        CreateChatMessageRequest request = CreateChatMessageRequest.builder()
                .clientMessageId("client-1")
                .messageType(ChatMessageType.CHAT)
                .content("hello outbox")
                .build();

        ChatMessageSendResponse response = chatMessageCommandService.sendMessage(room.getId(), request, 200L);

        assertThat(response.isDuplicated()).isFalse();
        assertThat(chatMessageRepository.findById(response.getMessageId())).isPresent();

        List<OutboxMessage> pending = outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getEventType()).isEqualTo("CHAT_MESSAGE_CREATED");
        assertThat(pending.get(0).getAggregateType()).isEqualTo("ChatRoom");
        assertThat(pending.get(0).getAggregateId()).isEqualTo(String.valueOf(room.getId()));
        assertThat(pending.get(0).getPayload()).contains("\"messageId\":" + response.getMessageId());
    }

    @Test
    void sendMessage_returnsExistingMessageOnDuplicateClientMessageId() {
        ChatRoom room = chatRoomRepository.save(
                ChatRoom.createInquiryRoom("inquiry:100:201:300", 100L, 300L, "상품 문의")
        );
        chatRoomParticipantRepository.save(
                ChatRoomParticipant.create(room.getId(), 201L, ChatParticipantRole.PARTICIPANT)
        );

        CreateChatMessageRequest request = CreateChatMessageRequest.builder()
                .clientMessageId("dup-client-1")
                .messageType(ChatMessageType.CHAT)
                .content("idempotent")
                .build();

        ChatMessageSendResponse first = chatMessageCommandService.sendMessage(room.getId(), request, 201L);
        ChatMessageSendResponse second = chatMessageCommandService.sendMessage(room.getId(), request, 201L);

        assertThat(first.isDuplicated()).isFalse();
        assertThat(second.isDuplicated()).isTrue();
        assertThat(second.getMessageId()).isEqualTo(first.getMessageId());
        assertThat(chatMessageRepository.findAll()).hasSize(1);
        assertThat(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)).hasSize(1);
    }
}
