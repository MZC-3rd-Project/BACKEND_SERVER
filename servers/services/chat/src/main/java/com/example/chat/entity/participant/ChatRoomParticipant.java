package com.example.chat.entity.participant;

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

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_room_participants", indexes = {
        @Index(name = "idx_chat_room_participants_user_status_room", columnList = "user_id, status, room_id"),
        @Index(name = "idx_chat_room_participants_room_status", columnList = "room_id, status")
})
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomParticipant extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private ChatParticipantRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ChatParticipantStatus status;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(name = "muted_until")
    private LocalDateTime mutedUntil;

    public static ChatRoomParticipant create(Long roomId, Long userId, ChatParticipantRole role) {
        ChatRoomParticipant participant = new ChatRoomParticipant();
        participant.roomId = roomId;
        participant.userId = userId;
        participant.role = role;
        participant.status = ChatParticipantStatus.ACTIVE;
        participant.joinedAt = LocalDateTime.now();
        return participant;
    }

    public boolean isActive() {
        return this.status == ChatParticipantStatus.ACTIVE;
    }

    public void updateRole(ChatParticipantRole role) {
        this.role = role;
    }

    public void markRefunded() {
        this.status = ChatParticipantStatus.LEFT_REFUNDED;
        this.leftAt = LocalDateTime.now();
    }

    public void remove() {
        this.status = ChatParticipantStatus.REMOVED;
        this.leftAt = LocalDateTime.now();
    }

    public void activateAfterRejoin() {
        this.status = ChatParticipantStatus.ACTIVE;
        this.leftAt = null;
    }

    public void updateLastReadMessageId(Long lastReadMessageId) {
        this.lastReadMessageId = lastReadMessageId;
    }

    public void muteUntil(LocalDateTime mutedUntil) {
        this.mutedUntil = mutedUntil;
    }
}
