package com.example.chat.service.command;

import com.example.chat.consumer.FundingEventMessage;
import com.example.chat.entity.audit.ChatAuditEventType;
import com.example.chat.entity.participant.ChatParticipantRole;
import com.example.chat.entity.participant.ChatParticipantStatus;
import com.example.chat.entity.participant.ChatRoomParticipant;
import com.example.chat.entity.room.ChatRoom;
import com.example.chat.entity.room.ChatRoomReadOnlyReason;
import com.example.chat.repository.ChatRoomParticipantRepository;
import com.example.chat.repository.ChatRoomRepository;
import com.example.chat.service.audit.ChatAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatFundingSyncService {

    private static final String FUNDING_ROOM_KEY_FORMAT = "funding:%d";

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;
    private final ChatAuditService chatAuditService;

    @Transactional
    public void syncFundingCreated(FundingEventMessage event) {
        if (event.getCampaignId() == null || event.getSellerId() == null) {
            log.warn("Skip FUNDING_CREATED sync. campaignId={}, sellerId={}", event.getCampaignId(), event.getSellerId());
            return;
        }

        String roomKey = FUNDING_ROOM_KEY_FORMAT.formatted(event.getCampaignId());
        ChatRoom room = chatRoomRepository.findByRoomKey(roomKey).orElse(null);
        if (room == null) {
            room = chatRoomRepository.save(
                    ChatRoom.createFundingGroupRoom(
                            roomKey,
                            event.getCampaignId(),
                            event.getItemId(),
                            event.getSellerId(),
                            "펀딩 캠페인 #" + event.getCampaignId()
                    )
            );
            chatAuditService.logEvent(
                    event.getSellerId(),
                    room.getId(),
                    null,
                    ChatAuditEventType.ROOM_CREATED,
                    Map.of("campaignId", event.getCampaignId(), "roomKey", roomKey)
            );
        }

        upsertParticipant(room.getId(), event.getSellerId(), ChatParticipantRole.SELLER_ADMIN);
    }

    @Transactional
    public void syncFundingParticipated(FundingEventMessage event) {
        if (event.getCampaignId() == null || event.getUserId() == null) {
            log.warn("Skip FUNDING_PARTICIPATED sync. campaignId={}, userId={}", event.getCampaignId(), event.getUserId());
            return;
        }

        ChatRoom room = chatRoomRepository.findByCampaignId(event.getCampaignId()).orElse(null);
        if (room == null) {
            log.warn("Skip FUNDING_PARTICIPATED sync. room not found. campaignId={}", event.getCampaignId());
            return;
        }

        ChatParticipantRole role = room.getSellerId().equals(event.getUserId())
                ? ChatParticipantRole.SELLER_ADMIN
                : ChatParticipantRole.PARTICIPANT;

        upsertParticipant(room.getId(), event.getUserId(), role);
    }

    @Transactional
    public void syncFundingRefunded(FundingEventMessage event) {
        if (event.getCampaignId() == null || event.getUserId() == null) {
            log.warn("Skip FUNDING_REFUNDED sync. campaignId={}, userId={}", event.getCampaignId(), event.getUserId());
            return;
        }

        ChatRoom room = chatRoomRepository.findByCampaignId(event.getCampaignId()).orElse(null);
        if (room == null) {
            log.warn("Skip FUNDING_REFUNDED sync. room not found. campaignId={}", event.getCampaignId());
            return;
        }

        if (room.getSellerId().equals(event.getUserId())) {
            log.warn("Skip FUNDING_REFUNDED sync for seller. campaignId={}, sellerId={}", event.getCampaignId(), event.getUserId());
            return;
        }

        ChatRoomParticipant participant = chatRoomParticipantRepository.findByRoomIdAndUserId(room.getId(), event.getUserId())
                .orElse(null);
        if (participant == null) {
            log.debug("Ignore FUNDING_REFUNDED. participant not found. roomId={}, userId={}", room.getId(), event.getUserId());
            return;
        }

        if (participant.getStatus() != ChatParticipantStatus.LEFT_REFUNDED) {
            participant.markRefunded();
            chatRoomParticipantRepository.save(participant);
            chatAuditService.logEvent(
                    event.getUserId(),
                    room.getId(),
                    event.getUserId(),
                    ChatAuditEventType.PARTICIPANT_STATUS_CHANGED,
                    Map.of("status", ChatParticipantStatus.LEFT_REFUNDED.name())
            );
        }
    }

    @Transactional
    public void syncFundingClosed(FundingEventMessage event) {
        if (event.getCampaignId() == null || event.getEventType() == null) {
            log.warn("Skip funding close sync. campaignId={}, eventType={}", event.getCampaignId(), event.getEventType());
            return;
        }

        ChatRoom room = chatRoomRepository.findByCampaignId(event.getCampaignId()).orElse(null);
        if (room == null) {
            log.warn("Skip funding close sync. room not found. campaignId={}", event.getCampaignId());
            return;
        }

        if (room.isReadOnly()) {
            return;
        }

        if ("FUNDING_SUCCEEDED".equals(event.getEventType())) {
            room.markReadOnly(ChatRoomReadOnlyReason.FUNDING_SUCCEEDED);
        } else {
            room.markReadOnly(ChatRoomReadOnlyReason.FUNDING_FAILED);
        }
        chatRoomRepository.save(room);
        chatAuditService.logEvent(
                null,
                room.getId(),
                null,
                ChatAuditEventType.ROOM_READ_ONLY_CHANGED,
                Map.of("reason", room.getReadOnlyReason().name())
        );
    }

    private void upsertParticipant(Long roomId, Long userId, ChatParticipantRole role) {
        ChatRoomParticipant participant = chatRoomParticipantRepository.findByRoomIdAndUserId(roomId, userId)
                .orElse(null);

        if (participant == null) {
            chatRoomParticipantRepository.save(ChatRoomParticipant.create(roomId, userId, role));
            chatAuditService.logEvent(
                    userId,
                    roomId,
                    userId,
                    ChatAuditEventType.PARTICIPANT_ADDED,
                    Map.of("role", role.name())
            );
            return;
        }

        ChatParticipantRole previousRole = participant.getRole();
        boolean wasActive = participant.isActive();
        participant.updateRole(role);
        if (!participant.isActive()) {
            participant.activateAfterRejoin();
        }
        chatRoomParticipantRepository.save(participant);

        if (previousRole != role) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("previousRole", previousRole.name());
            payload.put("newRole", role.name());
            chatAuditService.logEvent(
                    userId,
                    roomId,
                    userId,
                    ChatAuditEventType.PARTICIPANT_ROLE_CHANGED,
                    payload
            );
        }
        if (!wasActive) {
            chatAuditService.logEvent(
                    userId,
                    roomId,
                    userId,
                    ChatAuditEventType.PARTICIPANT_STATUS_CHANGED,
                    Map.of("status", ChatParticipantStatus.ACTIVE.name())
            );
        }
    }
}
