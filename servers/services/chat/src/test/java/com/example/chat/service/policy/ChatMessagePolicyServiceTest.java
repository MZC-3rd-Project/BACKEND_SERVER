package com.example.chat.service.policy;

import com.example.chat.entity.message.ChatMessageType;
import com.example.chat.entity.participant.ChatParticipantRole;
import com.example.chat.entity.participant.ChatRoomParticipant;
import com.example.chat.entity.room.ChatRoom;
import com.example.chat.entity.room.ChatRoomReadOnlyReason;
import com.example.chat.exception.ChatErrorCode;
import com.example.core.exception.BusinessException;
import com.example.security.context.AuthContext;
import com.example.security.context.AuthContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatMessagePolicyServiceTest {

    private final ChatMessagePolicyService chatMessagePolicyService = new ChatMessagePolicyService();

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    @Test
    void validateSendPermission_blocksChatInReadOnlyRoom() {
        ChatRoom room = ChatRoom.createFundingGroupRoom("funding:1", 1L, 2L, 10L, "room");
        room.markReadOnly(ChatRoomReadOnlyReason.FUNDING_FAILED);

        ChatRoomParticipant participant = ChatRoomParticipant.create(100L, 20L, ChatParticipantRole.PARTICIPANT);

        assertThatThrownBy(() -> chatMessagePolicyService.validateSendPermission(
                room, participant, 20L, ChatMessageType.CHAT
        )).isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ChatErrorCode.ROOM_READ_ONLY);
    }

    @Test
    void validateSendPermission_allowsNoticeForSellerAdminInReadOnlyRoom() {
        ChatRoom room = ChatRoom.createFundingGroupRoom("funding:1", 1L, 2L, 10L, "room");
        room.markReadOnly(ChatRoomReadOnlyReason.FUNDING_SUCCEEDED);

        ChatRoomParticipant sellerAdmin = ChatRoomParticipant.create(100L, 10L, ChatParticipantRole.SELLER_ADMIN);

        assertThatCode(() -> chatMessagePolicyService.validateSendPermission(
                room, sellerAdmin, 10L, ChatMessageType.NOTICE
        )).doesNotThrowAnyException();
    }

    @Test
    void validateSendPermission_blocksChatForPlatformAdmin() {
        AuthContextHolder.setContext(AuthContext.builder()
                .userId("999")
                .roles(List.of("ROLE_PLATFORM_ADMIN"))
                .nonce("n")
                .timestamp(System.currentTimeMillis())
                .build());

        ChatRoom room = ChatRoom.createFundingGroupRoom("funding:1", 1L, 2L, 10L, "room");
        ChatRoomParticipant participant = ChatRoomParticipant.create(100L, 999L, ChatParticipantRole.PLATFORM_ADMIN);

        assertThatThrownBy(() -> chatMessagePolicyService.validateSendPermission(
                room, participant, 999L, ChatMessageType.CHAT
        )).isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ChatErrorCode.PLATFORM_ADMIN_CHAT_NOT_ALLOWED);
    }

    @Test
    void validateSendPermission_allowsNoticeForPlatformAdminWithoutParticipant() {
        AuthContextHolder.setContext(AuthContext.builder()
                .userId("999")
                .roles(List.of("ROLE_PLATFORM_ADMIN"))
                .nonce("n")
                .timestamp(System.currentTimeMillis())
                .build());

        ChatRoom room = ChatRoom.createFundingGroupRoom("funding:1", 1L, 2L, 10L, "room");
        room.markReadOnly(ChatRoomReadOnlyReason.FUNDING_SUCCEEDED);

        assertThatCode(() -> chatMessagePolicyService.validateSendPermission(
                room, null, 999L, ChatMessageType.NOTICE
        )).doesNotThrowAnyException();
    }
}
