package com.example.chat.repository;

import com.example.chat.entity.message.ChatMessage;
import com.example.chat.entity.message.ChatMessageType;
import com.example.chat.entity.room.ChatRoom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "app.outbox.enabled=false"
})
class ChatMessageRepositoryTest {

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Test
    void findByRoomIdAndIdLessThanOrderByIdDesc_returnsCursorPage() {
        ChatRoom room = chatRoomRepository.save(ChatRoom.createInquiryRoom(
                "inquiry:100:200:300", 100L, 300L, "문의방"
        ));

        chatMessageRepository.save(ChatMessage.create(room.getId(), 200L, ChatMessageType.CHAT,
                "c1", "first", "first", null));
        chatMessageRepository.save(ChatMessage.create(room.getId(), 200L, ChatMessageType.CHAT,
                "c2", "second", "second", null));
        chatMessageRepository.save(ChatMessage.create(room.getId(), 200L, ChatMessageType.CHAT,
                "c3", "third", "third", null));

        List<ChatMessage> firstPage = chatMessageRepository.findByRoomIdOrderByIdDesc(
                room.getId(), PageRequest.of(0, 2)
        );

        assertThat(firstPage).hasSize(2);
        assertThat(firstPage).isSortedAccordingTo(Comparator.comparing(ChatMessage::getId).reversed());

        Long cursor = firstPage.get(1).getId();
        List<ChatMessage> nextPage = chatMessageRepository.findByRoomIdAndIdLessThanOrderByIdDesc(
                room.getId(), cursor, PageRequest.of(0, 10)
        );

        assertThat(nextPage).isNotEmpty();
        assertThat(nextPage).allMatch(message -> message.getId() < cursor);
        assertThat(nextPage).isSortedAccordingTo(Comparator.comparing(ChatMessage::getId).reversed());
    }

    @Test
    void findByRoomIdOrderByIdDesc_excludesSoftDeletedRows() {
        ChatRoom room = chatRoomRepository.save(ChatRoom.createInquiryRoom(
                "inquiry:101:201:301", 101L, 301L, "문의방2"
        ));

        ChatMessage kept = chatMessageRepository.save(ChatMessage.create(
                room.getId(), 201L, ChatMessageType.CHAT, "a", "keep", "keep", null
        ));
        ChatMessage deleted = chatMessageRepository.save(ChatMessage.create(
                room.getId(), 201L, ChatMessageType.CHAT, "b", "delete", "delete", null
        ));
        deleted.softDelete();
        chatMessageRepository.save(deleted);

        List<ChatMessage> messages = chatMessageRepository.findByRoomIdOrderByIdDesc(
                room.getId(), PageRequest.of(0, 10)
        );

        assertThat(messages).extracting(ChatMessage::getId)
                .contains(kept.getId())
                .doesNotContain(deleted.getId());
    }
}
