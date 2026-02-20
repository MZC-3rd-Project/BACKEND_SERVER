package com.example.chat.service.command;

import com.example.chat.client.ProductClient;
import com.example.chat.dto.command.request.CreateInquiryRoomRequest;
import com.example.chat.dto.command.response.ChatRoomCreateResponse;
import com.example.chat.entity.audit.ChatAuditEventType;
import com.example.chat.entity.participant.ChatParticipantRole;
import com.example.chat.entity.participant.ChatRoomParticipant;
import com.example.chat.entity.room.ChatRoom;
import com.example.chat.exception.ChatErrorCode;
import com.example.chat.repository.ChatRoomParticipantRepository;
import com.example.chat.repository.ChatRoomRepository;
import com.example.chat.service.audit.ChatAuditService;
import com.example.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatRoomCommandService {

    private static final String INQUIRY_ROOM_KEY_FORMAT = "inquiry:%d:%d:%d";

    private final ProductClient productClient;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;
    private final ChatAuditService chatAuditService;

    @Transactional
    public ChatRoomCreateResponse createInquiryRoom(CreateInquiryRoomRequest request, Long buyerId) {
        if (request == null || request.getItemId() == null || request.getItemId() <= 0 || buyerId == null || buyerId <= 0) {
            throw new BusinessException(ChatErrorCode.INVALID_INQUIRY_REQUEST);
        }

        ProductClient.ProductItemSummary itemSummary = productClient.findItemSummary(request.getItemId());
        if (itemSummary == null || itemSummary.sellerId() == null) {
            throw new BusinessException(ChatErrorCode.PRODUCT_SERVICE_ERROR);
        }

        Long sellerId = itemSummary.sellerId();
        if (buyerId.equals(sellerId)) {
            throw new BusinessException(ChatErrorCode.INVALID_INQUIRY_REQUEST);
        }

        String roomKey = INQUIRY_ROOM_KEY_FORMAT.formatted(request.getItemId(), buyerId, sellerId);
        ChatRoom existing = chatRoomRepository.findByRoomKey(roomKey).orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }

        String roomTitle = StringUtils.hasText(itemSummary.title()) ? itemSummary.title() : "상품 문의";

        try {
            ChatRoom room = chatRoomRepository.save(
                    ChatRoom.createInquiryRoom(roomKey, request.getItemId(), sellerId, roomTitle)
            );
            chatRoomParticipantRepository.save(
                    ChatRoomParticipant.create(room.getId(), buyerId, ChatParticipantRole.PARTICIPANT)
            );
            chatRoomParticipantRepository.save(
                    ChatRoomParticipant.create(room.getId(), sellerId, ChatParticipantRole.SELLER_ADMIN)
            );
            chatAuditService.logEvent(
                    buyerId,
                    room.getId(),
                    null,
                    ChatAuditEventType.ROOM_CREATED,
                    Map.of("roomKey", roomKey, "itemId", request.getItemId())
            );
            chatAuditService.logEvent(
                    buyerId,
                    room.getId(),
                    buyerId,
                    ChatAuditEventType.PARTICIPANT_ADDED,
                    Map.of("role", ChatParticipantRole.PARTICIPANT.name())
            );
            chatAuditService.logEvent(
                    sellerId,
                    room.getId(),
                    sellerId,
                    ChatAuditEventType.PARTICIPANT_ADDED,
                    Map.of("role", ChatParticipantRole.SELLER_ADMIN.name())
            );
            return toResponse(room);
        } catch (DataIntegrityViolationException e) {
            ChatRoom room = chatRoomRepository.findByRoomKey(roomKey)
                    .orElseThrow(() -> e);
            return toResponse(room);
        }
    }

    private ChatRoomCreateResponse toResponse(ChatRoom room) {
        List<ChatRoomCreateResponse.ParticipantSummary> participants = chatRoomParticipantRepository
                .findByRoomIdOrderByIdAsc(room.getId())
                .stream()
                .map(participant -> ChatRoomCreateResponse.ParticipantSummary.builder()
                        .userId(participant.getUserId())
                        .role(participant.getRole())
                        .status(participant.getStatus())
                        .build())
                .toList();

        return ChatRoomCreateResponse.builder()
                .roomId(room.getId())
                .roomType(room.getRoomType())
                .status(room.getStatus())
                .itemId(room.getItemId())
                .title(room.getTitle())
                .participants(participants)
                .build();
    }
}
