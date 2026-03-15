package com.example.chat.entity.participant;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRoomParticipantTest {

    @Test
    void create_initializesActiveParticipantState() {
        LocalDateTime beforeCreate = LocalDateTime.now();

        ChatRoomParticipant participant = ChatRoomParticipant.create(100L, 10L, ChatParticipantRole.PARTICIPANT);

        LocalDateTime afterCreate = LocalDateTime.now();

        assertThat(participant.getRoomId()).isEqualTo(100L);
        assertThat(participant.getUserId()).isEqualTo(10L);
        assertThat(participant.getRole()).isEqualTo(ChatParticipantRole.PARTICIPANT);
        assertThat(participant.getStatus()).isEqualTo(ChatParticipantStatus.ACTIVE);
        assertThat(participant.isActive()).isTrue();
        assertThat(participant.getJoinedAt()).isBetween(beforeCreate, afterCreate);
        assertThat(participant.getLeftAt()).isNull();
        assertThat(participant.getLastReadMessageId()).isNull();
        assertThat(participant.getMutedUntil()).isNull();
    }

    @Test
    void markRefunded_setsRefundedStatusAndLeftTimestamp() {
        ChatRoomParticipant participant = ChatRoomParticipant.create(100L, 10L, ChatParticipantRole.PARTICIPANT);
        LocalDateTime beforeRefund = LocalDateTime.now();

        participant.markRefunded();

        LocalDateTime afterRefund = LocalDateTime.now();

        assertThat(participant.getStatus()).isEqualTo(ChatParticipantStatus.LEFT_REFUNDED);
        assertThat(participant.isActive()).isFalse();
        assertThat(participant.getLeftAt()).isBetween(beforeRefund, afterRefund);
    }

    @Test
    void activateAfterRejoin_restoresRemovedParticipantToActive() {
        ChatRoomParticipant participant = ChatRoomParticipant.create(100L, 10L, ChatParticipantRole.PARTICIPANT);
        LocalDateTime joinedAt = participant.getJoinedAt();

        participant.remove();

        assertThat(participant.getStatus()).isEqualTo(ChatParticipantStatus.REMOVED);
        assertThat(participant.getLeftAt()).isNotNull();

        participant.activateAfterRejoin();

        assertThat(participant.getStatus()).isEqualTo(ChatParticipantStatus.ACTIVE);
        assertThat(participant.isActive()).isTrue();
        assertThat(participant.getLeftAt()).isNull();
        assertThat(participant.getJoinedAt()).isEqualTo(joinedAt);
    }

    @Test
    void refundedParticipant_canRejoinAndApplyMuteAndReadUpdates() {
        ChatRoomParticipant participant = ChatRoomParticipant.create(100L, 10L, ChatParticipantRole.PARTICIPANT);
        LocalDateTime mutedUntil = LocalDateTime.of(2026, 3, 14, 18, 0);

        participant.markRefunded();
        participant.activateAfterRejoin();
        participant.updateLastReadMessageId(101L);
        participant.updateLastReadMessageId(202L);
        participant.muteUntil(mutedUntil);
        participant.muteUntil(null);

        assertThat(participant.getStatus()).isEqualTo(ChatParticipantStatus.ACTIVE);
        assertThat(participant.getLeftAt()).isNull();
        assertThat(participant.getLastReadMessageId()).isEqualTo(202L);
        assertThat(participant.getMutedUntil()).isNull();
    }
}
