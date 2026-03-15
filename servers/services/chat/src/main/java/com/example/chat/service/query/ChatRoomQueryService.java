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

        List<ChatRoomSummaryResponse> content = chatRoomSummaryReader.readSummaries(pageParticipants);

        String nextCursor = hasNext
                ? CursorUtils.encode(pageParticipants.get(pageParticipants.size() - 1).getRoomId())
                : null;

        return CursorResponse.of(content, nextCursor);
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
