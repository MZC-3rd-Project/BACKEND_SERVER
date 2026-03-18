package com.example.chat.repository;

import com.example.chat.entity.participant.ChatParticipantRole;
import com.example.chat.entity.participant.ChatParticipantStatus;
import com.example.chat.entity.participant.ChatRoomParticipant;
import com.example.chat.entity.message.ChatMessage;
import com.example.chat.entity.message.ChatMessageType;
import com.example.chat.entity.room.ChatRoom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "app.outbox.enabled=false"
})
class ChatRoomParticipantRepositoryTest {

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatRoomParticipantRepository chatRoomParticipantRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Test
    void findByUserIdAndStatusOrderByRoomIdDesc_filtersByStatus() {
        ChatRoom room1 = chatRoomRepository.save(ChatRoom.createInquiryRoom(
                "inquiry:1:10:20", 1L, 20L, "room1"
        ));
        ChatRoom room2 = chatRoomRepository.save(ChatRoom.createInquiryRoom(
                "inquiry:2:10:20", 2L, 20L, "room2"
        ));

        ChatRoomParticipant active = ChatRoomParticipant.create(room1.getId(), 10L, ChatParticipantRole.PARTICIPANT);
        ChatRoomParticipant refunded = ChatRoomParticipant.create(room2.getId(), 10L, ChatParticipantRole.PARTICIPANT);
        refunded.markRefunded();

        chatRoomParticipantRepository.save(active);
        chatRoomParticipantRepository.save(refunded);

        List<ChatRoomParticipant> activeParticipants = chatRoomParticipantRepository
                .findByUserIdAndStatusOrderByRoomIdDesc(10L, ChatParticipantStatus.ACTIVE);

        assertThat(activeParticipants).hasSize(1);
        assertThat(activeParticipants.get(0).getRoomId()).isEqualTo(room1.getId());
        assertThat(activeParticipants.get(0).getStatus()).isEqualTo(ChatParticipantStatus.ACTIVE);
    }

    @Test
    void findByRoomIdAndStatusOrderByIdAsc_excludesSoftDeletedRows() {
        ChatRoom room = chatRoomRepository.save(ChatRoom.createInquiryRoom(
                "inquiry:3:11:21", 3L, 21L, "room3"
        ));

        ChatRoomParticipant participant = chatRoomParticipantRepository.save(
                ChatRoomParticipant.create(room.getId(), 11L, ChatParticipantRole.PARTICIPANT)
        );
        participant.softDelete();
        chatRoomParticipantRepository.save(participant);

        List<ChatRoomParticipant> participants = chatRoomParticipantRepository
                .findByRoomIdAndStatusOrderByIdAsc(room.getId(), ChatParticipantStatus.ACTIVE);

        assertThat(participants).isEmpty();
    }

    @Test
    void findActiveParticipantsOrderByLatestMessage_ordersByLastMessageIdDesc() {
        ChatRoom roomWithoutMessage = chatRoomRepository.save(ChatRoom.createInquiryRoom(
                "inquiry:4:10:20", 4L, 20L, "room4"
        ));
        ChatRoom olderRoom = chatRoomRepository.save(ChatRoom.createInquiryRoom(
                "inquiry:5:10:20", 5L, 20L, "room5"
        ));
        ChatRoom newestRoom = chatRoomRepository.save(ChatRoom.createInquiryRoom(
                "inquiry:6:10:20", 6L, 20L, "room6"
        ));

        chatRoomParticipantRepository.save(ChatRoomParticipant.create(roomWithoutMessage.getId(), 10L, ChatParticipantRole.PARTICIPANT));
        chatRoomParticipantRepository.save(ChatRoomParticipant.create(olderRoom.getId(), 10L, ChatParticipantRole.PARTICIPANT));
        chatRoomParticipantRepository.save(ChatRoomParticipant.create(newestRoom.getId(), 10L, ChatParticipantRole.PARTICIPANT));

        ChatMessage olderMessage = chatMessageRepository.save(ChatMessage.create(
                olderRoom.getId(), 20L, ChatMessageType.CHAT, "c1", "older", "older", null
        ));
        ChatMessage newestMessage = chatMessageRepository.save(ChatMessage.create(
                newestRoom.getId(), 20L, ChatMessageType.CHAT, "c2", "newest", "newest", null
        ));

        List<ChatRoomParticipant> result = chatRoomParticipantRepository.findActiveParticipantsOrderByLatestMessage(
                10L,
                ChatParticipantStatus.ACTIVE.name(),
                null,
                null,
                10
        );

        assertThat(result).extracting(ChatRoomParticipant::getRoomId)
                .containsExactly(newestRoom.getId(), olderRoom.getId(), roomWithoutMessage.getId());

        List<ChatRoomParticipant> nextPage = chatRoomParticipantRepository.findActiveParticipantsOrderByLatestMessage(
                10L,
                ChatParticipantStatus.ACTIVE.name(),
                olderMessage.getId(),
                olderRoom.getId(),
                10
        );

        assertThat(nextPage).extracting(ChatRoomParticipant::getRoomId)
                .containsExactly(roomWithoutMessage.getId());
        assertThat(newestMessage.getId()).isGreaterThan(olderMessage.getId());
    }
}
