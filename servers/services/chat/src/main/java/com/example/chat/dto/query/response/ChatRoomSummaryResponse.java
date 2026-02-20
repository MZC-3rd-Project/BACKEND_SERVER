package com.example.chat.dto.query.response;

import com.example.chat.entity.room.ChatRoomStatus;
import com.example.chat.entity.room.ChatRoomType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatRoomSummaryResponse {

    private Long roomId;
    private ChatRoomType roomType;
    private ChatRoomStatus status;
    private Long itemId;
    private Long campaignId;
    private String title;

    private Long lastReadMessageId;
    private long unreadCount;

    private ChatRoomLastMessageResponse lastMessage;
}
