package com.example.chat.service.query;

import com.example.chat.dto.query.response.ChatMessageItemResponse;
import com.example.chat.dto.query.response.ChatRoomLastMessageResponse;
import com.example.chat.entity.message.ChatMessage;
import com.example.chat.entity.message.ChatMessageType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessagePresenterTest {

    private final ChatMessagePresenter chatMessagePresenter = new ChatMessagePresenter();

    @Test
    void toItemResponse_mapsSanitizedMessageFields() {
        ChatMessage message = ChatMessage.create(100L, 10L, ChatMessageType.CHAT, "c1", "raw", "safe", null);
        ReflectionTestUtils.setField(message, "id", 200L);
        ReflectionTestUtils.setField(message, "createdAt", LocalDateTime.now());

        ChatMessageItemResponse result = chatMessagePresenter.toItemResponse(message);

        assertThat(result.getMessageId()).isEqualTo(200L);
        assertThat(result.getSenderId()).isEqualTo(10L);
        assertThat(result.getContent()).isEqualTo("safe");
    }

    @Test
    void toLastMessageResponse_truncatesPreviewToHundredChars() {
        String content = "a".repeat(110);
        ChatMessage message = ChatMessage.create(100L, 10L, ChatMessageType.CHAT, "c1", content, content, null);
        ReflectionTestUtils.setField(message, "id", 200L);
        ReflectionTestUtils.setField(message, "createdAt", LocalDateTime.now());

        ChatRoomLastMessageResponse result = chatMessagePresenter.toLastMessageResponse(message);

        assertThat(result.getPreview()).hasSize(100);
        assertThat(result.getPreview()).isEqualTo(content.substring(0, 100));
    }
}
