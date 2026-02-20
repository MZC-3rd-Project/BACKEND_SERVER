package com.example.chat.entity.message;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_messages_room_id_desc", columnList = "room_id, id DESC"),
        @Index(name = "idx_chat_messages_sender_created", columnList = "sender_id, created_at DESC")
})
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private ChatMessageType messageType;

    @Column(name = "client_message_id", length = 100)
    private String clientMessageId;

    @Column(name = "content", nullable = false, length = 4000)
    private String content;

    @Column(name = "content_sanitized", nullable = false, length = 4000)
    private String contentSanitized;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    public static ChatMessage create(Long roomId,
                                     Long senderId,
                                     ChatMessageType messageType,
                                     String clientMessageId,
                                     String content,
                                     String contentSanitized,
                                     String metadata) {
        ChatMessage message = new ChatMessage();
        message.roomId = roomId;
        message.senderId = senderId;
        message.messageType = messageType;
        message.clientMessageId = clientMessageId;
        message.content = content;
        message.contentSanitized = contentSanitized;
        message.metadata = metadata;
        return message;
    }
}
