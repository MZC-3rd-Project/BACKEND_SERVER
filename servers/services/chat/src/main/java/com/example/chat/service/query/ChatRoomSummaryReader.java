package com.example.chat.service.query;

import com.example.chat.dto.query.response.ChatRoomLastMessageResponse;
import com.example.chat.dto.query.response.ChatRoomSummaryResponse;
import com.example.chat.entity.message.ChatMessage;
import com.example.chat.entity.participant.ChatRoomParticipant;
import com.example.chat.entity.room.ChatRoom;
import com.example.chat.repository.ChatMessageRepository;
import com.example.chat.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomSummaryReader {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessagePresenter chatMessagePresenter;

    public List<ChatRoomSummaryResponse> readSummaries(List<ChatRoomParticipant> participants) {
        if (participants == null || participants.isEmpty()) {
            return List.of();
        }

        List<Long> roomIds = participants.stream()
            .map(ChatRoomParticipant::getRoomId)
            .toList();

        Map<Long, ChatRoom> roomMap = new LinkedHashMap<>();
        for (ChatRoom room : chatRoomRepository.findAllById(roomIds)) {
            roomMap.put(room.getId(), room);
        }

        return participants.stream()
            .map(participant -> toSummary(participant, roomMap.get(participant.getRoomId())))
            .filter(Objects::nonNull)
            .toList();
    }

    private ChatRoomSummaryResponse toSummary(ChatRoomParticipant participant, ChatRoom room) {
        if (room == null) {
            return null;
        }

        return ChatRoomSummaryResponse.builder()
            .roomId(room.getId())
            .roomType(room.getRoomType())
            .status(room.getStatus())
            .itemId(room.getItemId())
            .campaignId(room.getCampaignId())
            .title(room.getTitle())
            .lastReadMessageId(participant.getLastReadMessageId())
            .unreadCount(resolveUnreadCount(participant))
            .lastMessage(resolveLastMessage(room.getId()))
            .build();
    }

    private long resolveUnreadCount(ChatRoomParticipant participant) {
        if (participant.getLastReadMessageId() == null) {
            return chatMessageRepository.countByRoomId(participant.getRoomId());
        }
        return chatMessageRepository.countByRoomIdAndIdGreaterThan(
            participant.getRoomId(),
            participant.getLastReadMessageId()
        );
    }

    private ChatRoomLastMessageResponse resolveLastMessage(Long roomId) {
        List<ChatMessage> lastMessages = chatMessageRepository.findByRoomIdOrderByIdDesc(
            roomId,
            PageRequest.of(0, 1)
        );
        if (lastMessages.isEmpty()) {
            return null;
        }
        return chatMessagePresenter.toLastMessageResponse(lastMessages.getFirst());
    }
}
