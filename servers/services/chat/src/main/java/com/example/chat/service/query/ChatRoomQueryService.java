package com.example.chat.service.query;

import com.example.chat.dto.query.response.ChatMessageItemResponse;
import com.example.chat.dto.query.response.ChatRoomSummaryResponse;
import com.example.chat.entity.message.ChatMessage;
import com.example.chat.entity.participant.ChatParticipantStatus;
import com.example.chat.entity.participant.ChatRoomParticipant;
import com.example.chat.repository.ChatMessageRepository;
import com.example.chat.repository.ChatRoomParticipantRepository;
import com.example.chat.service.policy.ChatRoomAccessPolicy;
import com.example.core.pagination.CursorResponse;
import com.example.core.pagination.CursorUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomQueryService {

    private final ChatRoomParticipantRepository chatRoomParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomAccessPolicy chatRoomAccessPolicy;
    private final ChatRoomSummaryReader chatRoomSummaryReader;
    private final ChatMessagePresenter chatMessagePresenter;

    public CursorResponse<ChatRoomSummaryResponse> findMyRooms(Long userId, String cursor, int size) {
        ChatRoomListCursorCodec.Cursor decodedCursor = ChatRoomListCursorCodec.decode(cursor);

        List<ChatRoomParticipant> participants = chatRoomParticipantRepository.findActiveParticipantsOrderByLatestMessage(
                userId,
                ChatParticipantStatus.ACTIVE.name(),
                decodedCursor == null ? null : decodedCursor.lastMessageId(),
                decodedCursor == null ? null : decodedCursor.roomId(),
                size + 1
        );

        boolean hasNext = participants.size() > size;
        List<ChatRoomParticipant> pageParticipants = hasNext ? participants.subList(0, size) : participants;

        if (pageParticipants.isEmpty()) {
            return CursorResponse.empty();
        }

        List<ChatRoomSummaryResponse> content = chatRoomSummaryReader.readSummaries(userId, pageParticipants);

        String nextCursor = hasNext ? encodeNextCursor(content) : null;

        return CursorResponse.of(content, nextCursor);
    }

    private String encodeNextCursor(List<ChatRoomSummaryResponse> content) {
        ChatRoomSummaryResponse lastRoom = content.get(content.size() - 1);
        Long lastMessageId = lastRoom.getLastMessage() == null ? 0L : lastRoom.getLastMessage().getMessageId();
        return ChatRoomListCursorCodec.encode(lastMessageId, lastRoom.getRoomId());
    }

    public CursorResponse<ChatMessageItemResponse> findRoomMessages(Long roomId, Long userId, String cursor, int size) {
        chatRoomAccessPolicy.requireActiveParticipant(roomId, userId);

        Long cursorId = CursorUtils.decodeLong(cursor);
        PageRequest pageable = PageRequest.of(0, size + 1);

        List<ChatMessage> messages = cursorId == null
                ? chatMessageRepository.findByRoomIdOrderByIdDesc(roomId, pageable)
                : chatMessageRepository.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, cursorId, pageable);

        boolean hasNext = messages.size() > size;
        List<ChatMessage> pageMessages = hasNext ? messages.subList(0, size) : messages;

        List<ChatMessageItemResponse> content = pageMessages.stream()
                .map(chatMessagePresenter::toItemResponse)
                .toList();

        String nextCursor = hasNext
                ? CursorUtils.encode(pageMessages.get(pageMessages.size() - 1).getId())
                : null;

        return CursorResponse.of(content, nextCursor);
    }

    public List<ChatMessageItemResponse> findMessagesAfter(Long roomId,
                                                           Long userId,
                                                           Long lastReceivedMessageId,
                                                           int size) {
        chatRoomAccessPolicy.requireActiveParticipant(roomId, userId);

        if (lastReceivedMessageId == null || lastReceivedMessageId <= 0) {
            return List.of();
        }

        List<ChatMessage> messages = chatMessageRepository.findByRoomIdAndIdGreaterThanOrderByIdAsc(
                roomId,
                lastReceivedMessageId,
                PageRequest.of(0, Math.max(1, size))
        );

        return messages.stream()
                .map(chatMessagePresenter::toItemResponse)
                .toList();
    }
}
