package com.example.chat.dto.command.response;

import com.example.chat.entity.participant.ChatParticipantRole;
import com.example.chat.entity.participant.ChatParticipantStatus;
import com.example.chat.entity.room.ChatRoomStatus;
import com.example.chat.entity.room.ChatRoomType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ChatRoomCreateResponse {

    private Long roomId;
    private ChatRoomType roomType;
    private ChatRoomStatus status;
    private Long itemId;
    private String title;
    private List<ParticipantSummary> participants;

    @Getter
    @Builder
    public static class ParticipantSummary {
        private Long userId;
        private ChatParticipantRole role;
        private ChatParticipantStatus status;
    }
}
