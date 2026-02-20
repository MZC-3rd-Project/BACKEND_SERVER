package com.example.chat.service.query;

import com.example.chat.dto.query.response.ChatMessageItemResponse;
import com.example.chat.dto.query.response.ChatRoomLastMessageResponse;
import com.example.chat.dto.query.response.ChatRoomSummaryResponse;
import com.example.chat.entity.message.ChatMessage;
import com.example.chat.entity.participant.ChatParticipantStatus;
import com.example.chat.entity.participant.ChatRoomParticipant;
import com.example.chat.entity.room.ChatRoom;
import com.example.chat.exception.ChatErrorCode;
import com.example.chat.repository.ChatMessageRepository;
import com.example.chat.repository.ChatRoomParticipantRepository;
import com.example.chat.repository.ChatRoomRepository;
import com.example.core.exception.BusinessException;
import com.example.core.pagination.CursorResponse;
import com.example.core.pagination.CursorUtils;
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
public class ChatRoomQueryService {

    private final ChatRoomParticipantRepository chatRoomParticipantRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;

    public CursorResponse<ChatRoomSummaryResponse> findMyRooms(Long userId, String cursor, int size) {
        Long cursorId = CursorUtils.decodeLong(cursor);
        PageRequest pageable = PageRequest.of(0, size + 1);

        List<ChatRoomParticipant> participants = cursorId == null
                ? chatRoomParticipantRepository.findByUserIdAndStatusOrderByRoomIdDesc(
                userId, ChatParticipantStatus.ACTIVE, pageable)
                : chatRoomParticipantRepository.findByUserIdAndStatusAndRoomIdLessThanOrderByRoomIdDesc(
                userId, ChatParticipantStatus.ACTIVE, cursorId, pageable);

        boolean hasNext = participants.size() > size;
        List<ChatRoomParticipant> pageParticipants = hasNext ? participants.subList(0, size) : participants;

        if (pageParticipants.isEmpty()) {
            return CursorResponse.empty();
        }

        List<Long> roomIds = pageParticipants.stream().map(ChatRoomParticipant::getRoomId).toList();
        Map<Long, ChatRoom> roomMap = new LinkedHashMap<>();
        for (ChatRoom room : chatRoomRepository.findAllById(roomIds)) {
            roomMap.put(room.getId(), room);
        }

        List<ChatRoomSummaryResponse> content = pageParticipants.stream()
                .map(participant -> toSummary(participant, roomMap.get(participant.getRoomId())))
                .filter(Objects::nonNull)
                .toList();

        String nextCursor = hasNext
                ? CursorUtils.encode(pageParticipants.get(pageParticipants.size() - 1).getRoomId())
                : null;

        return CursorResponse.of(content, nextCursor);
    }

    public CursorResponse<ChatMessageItemResponse> findRoomMessages(Long roomId, Long userId, String cursor, int size) {
        validateRoomAccess(roomId, userId);

        Long cursorId = CursorUtils.decodeLong(cursor);
        PageRequest pageable = PageRequest.of(0, size + 1);

        List<ChatMessage> messages = cursorId == null
                ? chatMessageRepository.findByRoomIdOrderByIdDesc(roomId, pageable)
                : chatMessageRepository.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, cursorId, pageable);

        boolean hasNext = messages.size() > size;
        List<ChatMessage> pageMessages = hasNext ? messages.subList(0, size) : messages;

        List<ChatMessageItemResponse> content = pageMessages.stream()
                .map(message -> ChatMessageItemResponse.builder()
                        .messageId(message.getId())
                        .senderId(message.getSenderId())
                        .messageType(message.getMessageType())
                        .content(message.getContentSanitized())
                        .createdAt(message.getCreatedAt())
                        .build())
                .toList();

        String nextCursor = hasNext
                ? CursorUtils.encode(pageMessages.get(pageMessages.size() - 1).getId())
                : null;

        return CursorResponse.of(content, nextCursor);
    }

    public void validateRoomAccess(Long roomId, Long userId) {
        ChatRoomParticipant participant = chatRoomParticipantRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.FORBIDDEN_ROOM_ACCESS));
        if (!participant.isActive()) {
            throw new BusinessException(ChatErrorCode.FORBIDDEN_ROOM_ACCESS);
        }
    }

    private ChatRoomSummaryResponse toSummary(ChatRoomParticipant participant, ChatRoom room) {
        if (room == null) {
            return null;
        }

        ChatRoomLastMessageResponse lastMessage = resolveLastMessage(room.getId());

        long unreadCount = participant.getLastReadMessageId() == null
                ? chatMessageRepository.countByRoomId(room.getId())
                : chatMessageRepository.countByRoomIdAndIdGreaterThan(room.getId(), participant.getLastReadMessageId());

        return ChatRoomSummaryResponse.builder()
                .roomId(room.getId())
                .roomType(room.getRoomType())
                .status(room.getStatus())
                .itemId(room.getItemId())
                .campaignId(room.getCampaignId())
                .title(room.getTitle())
                .lastReadMessageId(participant.getLastReadMessageId())
                .unreadCount(unreadCount)
                .lastMessage(lastMessage)
                .build();
    }

    private ChatRoomLastMessageResponse resolveLastMessage(Long roomId) {
        List<ChatMessage> lastMessages = chatMessageRepository.findByRoomIdOrderByIdDesc(
                roomId,
                PageRequest.of(0, 1)
        );
        if (lastMessages.isEmpty()) {
            return null;
        }

        ChatMessage lastMessage = lastMessages.get(0);
        String preview = lastMessage.getContentSanitized();
        if (preview != null && preview.length() > 100) {
            preview = preview.substring(0, 100);
        }

        return ChatRoomLastMessageResponse.builder()
                .messageId(lastMessage.getId())
                .messageType(lastMessage.getMessageType())
                .preview(preview)
                .createdAt(lastMessage.getCreatedAt())
                .build();
    }
}
