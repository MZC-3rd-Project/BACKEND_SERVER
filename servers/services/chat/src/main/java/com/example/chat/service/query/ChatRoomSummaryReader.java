package com.example.chat.service.query;

import com.example.chat.client.ChatProductLookupClient;
import com.example.chat.client.ChatProductSnapshot;
import com.example.chat.client.ChatProfileLookupClient;
import com.example.chat.client.ChatProfileSnapshot;
import com.example.chat.client.ChatStoreLookupClient;
import com.example.chat.client.ChatStoreSnapshot;
import com.example.chat.dto.query.response.ChatRoomLastMessageResponse;
import com.example.chat.dto.query.response.ChatRoomSummaryResponse;
import com.example.chat.entity.message.ChatMessage;
import com.example.chat.entity.participant.ChatRoomParticipant;
import com.example.chat.entity.room.ChatRoom;
import com.example.clients.media.facade.MediaClientFacade;
import com.example.chat.repository.ChatMessageRepository;
import com.example.chat.repository.ChatRoomParticipantRepository;
import com.example.chat.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomSummaryReader {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessagePresenter chatMessagePresenter;
    private final ChatProductLookupClient chatProductLookupClient;
    private final ChatStoreLookupClient chatStoreLookupClient;
    private final ChatProfileLookupClient chatProfileLookupClient;
    private final MediaClientFacade mediaClientFacade;
    private final ChatSalesChannelResolver chatSalesChannelResolver;

    public List<ChatRoomSummaryResponse> readSummaries(Long viewerId, List<ChatRoomParticipant> participants) {
        if (participants == null || participants.isEmpty()) {
            return List.of();
        }

        List<Long> roomIds = participants.stream()
            .map(ChatRoomParticipant::getRoomId)
            .toList();

        Map<Long, ChatRoom> roomMap = chatRoomRepository.findAllById(roomIds).stream()
            .collect(Collectors.toMap(ChatRoom::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        Map<Long, ChatMessage> latestMessageMap = chatMessageRepository.findLatestByRoomIdIn(roomIds).stream()
            .collect(Collectors.toMap(ChatMessage::getRoomId, Function.identity()));

        Map<Long, List<ChatRoomParticipant>> roomParticipantMap = chatRoomParticipantRepository
            .findByRoomIdInOrderByRoomIdAscIdAsc(roomIds).stream()
            .collect(Collectors.groupingBy(ChatRoomParticipant::getRoomId, LinkedHashMap::new, Collectors.toList()));

        Map<Long, ChatProductSnapshot> productSnapshotMap = chatProductLookupClient.findItems(roomMap.values().stream()
            .map(ChatRoom::getItemId)
            .filter(itemId -> itemId != null && itemId > 0L)
            .toList());

        Map<Long, ChatStoreSnapshot> storeSnapshotMap = chatStoreLookupClient.findStores(productSnapshotMap.values().stream()
            .map(ChatProductSnapshot::storeId)
            .filter(storeId -> storeId != null && storeId > 0L)
            .toList());

        Map<Long, ChatProfileSnapshot> profileSnapshotMap = chatProfileLookupClient.findProfiles(resolveBuyerIds(viewerId, participants, roomMap, roomParticipantMap));

        Map<Long, String> thumbnailUrlMap = resolveThumbnailUrlMap(productSnapshotMap);

        return participants.stream()
            .map(participant -> toSummary(
                viewerId,
                participant,
                roomMap.get(participant.getRoomId()),
                latestMessageMap,
                roomParticipantMap,
                productSnapshotMap,
                storeSnapshotMap,
                profileSnapshotMap,
                thumbnailUrlMap
            ))
            .filter(Objects::nonNull)
            .toList();
    }

    private List<Long> resolveBuyerIds(Long viewerId,
                                       List<ChatRoomParticipant> participants,
                                       Map<Long, ChatRoom> roomMap,
                                       Map<Long, List<ChatRoomParticipant>> roomParticipantMap) {
        return participants.stream()
            .map(ChatRoomParticipant::getRoomId)
            .map(roomMap::get)
            .filter(Objects::nonNull)
            .filter(room -> room.getSellerId() != null && room.getSellerId().equals(viewerId))
            .map(room -> resolveBuyerId(room, roomParticipantMap.get(room.getId())))
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    }

    private Map<Long, String> resolveThumbnailUrlMap(Map<Long, ChatProductSnapshot> productSnapshotMap) {
        List<Long> thumbnailMediaIds = productSnapshotMap.values().stream()
            .map(ChatProductSnapshot::thumbnailMediaId)
            .filter(mediaId -> mediaId != null && mediaId > 0L)
            .distinct()
            .toList();
        if (thumbnailMediaIds.isEmpty()) {
            return Map.of();
        }
        try {
            return mediaClientFacade.getMediaUrlMap(thumbnailMediaIds);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private ChatRoomSummaryResponse toSummary(Long viewerId,
                                              ChatRoomParticipant participant,
                                              ChatRoom room,
                                              Map<Long, ChatMessage> latestMessageMap,
                                              Map<Long, List<ChatRoomParticipant>> roomParticipantMap,
                                              Map<Long, ChatProductSnapshot> productSnapshotMap,
                                              Map<Long, ChatStoreSnapshot> storeSnapshotMap,
                                              Map<Long, ChatProfileSnapshot> profileSnapshotMap,
                                              Map<Long, String> thumbnailUrlMap) {
        if (room == null) {
            return null;
        }

        ChatMessage latestMessage = latestMessageMap.get(room.getId());
        ChatRoomLastMessageResponse lastMessageResponse = latestMessage == null
            ? null
            : chatMessagePresenter.toLastMessageResponse(latestMessage);
        ChatProductSnapshot productSnapshot = productSnapshotMap.get(room.getItemId());
        ChatStoreSnapshot storeSnapshot = productSnapshot == null ? null : storeSnapshotMap.get(productSnapshot.storeId());
        Long buyerId = room.getSellerId() != null && room.getSellerId().equals(viewerId)
            ? resolveBuyerId(room, roomParticipantMap.get(room.getId()))
            : null;
        ChatProfileSnapshot buyerSnapshot = buyerId == null ? null : profileSnapshotMap.get(buyerId);
        return ChatRoomSummaryResponse.builder()
            .roomId(room.getId())
            .roomType(room.getRoomType())
            .status(room.getStatus())
            .itemId(room.getItemId())
            .campaignId(room.getCampaignId())
            .title(room.getTitle())
            .storeId(productSnapshot == null ? null : productSnapshot.storeId())
            .storeName(storeSnapshot == null ? null : storeSnapshot.storeName())
            .itemThumbnailUrl(resolveThumbnailUrl(productSnapshot, thumbnailUrlMap))
            .buyerDisplayName(buyerSnapshot == null ? null : buyerSnapshot.nickname())
            .salesChannel(chatSalesChannelResolver.resolve(room, productSnapshot))
            .updatedAt(resolveUpdatedAt(room, latestMessage))
            .lastReadMessageId(participant.getLastReadMessageId())
            .unreadCount(resolveUnreadCount(participant))
            .lastMessage(lastMessageResponse)
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

    private Long resolveBuyerId(ChatRoom room, List<ChatRoomParticipant> participants) {
        if (room == null || participants == null || participants.isEmpty()) {
            return null;
        }
        List<Long> counterpartIds = participants.stream()
            .map(ChatRoomParticipant::getUserId)
            .filter(userId -> userId != null && !userId.equals(room.getSellerId()))
            .distinct()
            .toList();
        if (counterpartIds.size() != 1) {
            return null;
        }
        return counterpartIds.getFirst();
    }

    private String resolveThumbnailUrl(ChatProductSnapshot productSnapshot, Map<Long, String> thumbnailUrlMap) {
        if (productSnapshot == null || productSnapshot.thumbnailMediaId() == null) {
            return null;
        }
        return thumbnailUrlMap.get(productSnapshot.thumbnailMediaId());
    }

    private LocalDateTime resolveUpdatedAt(ChatRoom room, ChatMessage latestMessage) {
        if (latestMessage != null) {
            return latestMessage.getCreatedAt();
        }
        return room.getCreatedAt();
    }
}
