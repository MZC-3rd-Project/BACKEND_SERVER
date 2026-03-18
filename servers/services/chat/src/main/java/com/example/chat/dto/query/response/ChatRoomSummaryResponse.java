package com.example.chat.dto.query.response;

import com.example.chat.entity.room.ChatRoomStatus;
import com.example.chat.entity.room.ChatRoomType;
import com.example.chat.entity.room.ChatSalesChannel;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatRoomSummaryResponse {

    private Long roomId;
    private ChatRoomType roomType;
    private ChatRoomStatus status;
    private Long itemId;
    private Long campaignId;
    private String title;
    private Long storeId;
    private String storeName;
    private String itemThumbnailUrl;
    private String buyerDisplayName;
    private ChatSalesChannel salesChannel;
    private LocalDateTime updatedAt;

    private Long lastReadMessageId;
    private long unreadCount;

    private ChatRoomLastMessageResponse lastMessage;
}
