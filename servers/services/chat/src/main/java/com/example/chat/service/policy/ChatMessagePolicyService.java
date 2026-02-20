package com.example.chat.service.policy;

import com.example.chat.entity.message.ChatMessageType;
import com.example.chat.entity.participant.ChatParticipantRole;
import com.example.chat.entity.participant.ChatRoomParticipant;
import com.example.chat.entity.room.ChatRoom;
import com.example.chat.exception.ChatErrorCode;
import com.example.core.exception.BusinessException;
import com.example.security.context.AuthContext;
import com.example.security.context.AuthContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ChatMessagePolicyService {

    private static final String ROLE_PLATFORM_ADMIN = "ROLE_PLATFORM_ADMIN";

    public void validateSendPermission(ChatRoom room,
                                       ChatRoomParticipant participant,
                                       Long senderId,
                                       ChatMessageType messageType) {
        boolean platformAdmin = isPlatformAdmin();
        if (platformAdmin) {
            if (messageType != ChatMessageType.NOTICE) {
                throw new BusinessException(ChatErrorCode.PLATFORM_ADMIN_CHAT_NOT_ALLOWED);
            }
            return;
        }

        if (participant == null || !participant.isActive()) {
            throw new BusinessException(ChatErrorCode.FORBIDDEN_ROOM_ACCESS);
        }

        boolean sellerAdmin = isSellerAdminOfRoom(room, participant, senderId);

        if (room.isReadOnly()) {
            if (messageType != ChatMessageType.NOTICE) {
                throw new BusinessException(ChatErrorCode.ROOM_READ_ONLY);
            }
            if (!sellerAdmin) {
                throw new BusinessException(ChatErrorCode.NOTICE_PERMISSION_DENIED);
            }
            return;
        }

        if (messageType == ChatMessageType.NOTICE && !sellerAdmin) {
            throw new BusinessException(ChatErrorCode.NOTICE_PERMISSION_DENIED);
        }
    }

    private boolean isPlatformAdmin() {
        try {
            AuthContext authContext = AuthContextHolder.currentContext();
            return authContext.hasRole(ROLE_PLATFORM_ADMIN);
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    private boolean isSellerAdminOfRoom(ChatRoom room, ChatRoomParticipant participant, Long senderId) {
        return participant.getRole() == ChatParticipantRole.SELLER_ADMIN
                && room.getSellerId().equals(senderId);
    }
}
