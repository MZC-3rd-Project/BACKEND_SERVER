package com.example.chat.service.query;

import com.example.chat.client.ChatProductSnapshot;
import com.example.chat.entity.room.ChatRoom;
import com.example.chat.entity.room.ChatRoomType;
import com.example.chat.entity.room.ChatSalesChannel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ChatSalesChannelResolver {

    private static final String STATUS_FUNDING = "FUNDING";
    private static final String STATUS_FUNDED = "FUNDED";
    private static final String STATUS_HOT_DEAL = "HOT_DEAL";

    public ChatSalesChannel resolve(ChatRoom room, ChatProductSnapshot itemSnapshot) {
        if (room == null) {
            return resolve(itemSnapshot);
        }
        if (room.getRoomType() == ChatRoomType.FUNDING_GROUP || room.getCampaignId() != null) {
            return ChatSalesChannel.FUNDING;
        }
        return resolve(itemSnapshot);
    }

    public ChatSalesChannel resolve(ChatProductSnapshot itemSnapshot) {
        if (itemSnapshot == null || !StringUtils.hasText(itemSnapshot.status())) {
            return ChatSalesChannel.NORMAL_SALE;
        }
        return switch (itemSnapshot.status()) {
            case STATUS_HOT_DEAL -> ChatSalesChannel.HOT_DEAL;
            case STATUS_FUNDING, STATUS_FUNDED -> ChatSalesChannel.FUNDING;
            default -> ChatSalesChannel.NORMAL_SALE;
        };
    }

    public String toInquiryTitle(ChatSalesChannel salesChannel, String itemTitle) {
        String safeTitle = StringUtils.hasText(itemTitle) ? itemTitle : "상품";
        if (salesChannel == null) {
            return safeTitle + " 문의";
        }
        return "[" + toLabel(salesChannel) + "] " + safeTitle + " 문의";
    }

    private String toLabel(ChatSalesChannel salesChannel) {
        return switch (salesChannel) {
            case FUNDING -> "펀딩";
            case NORMAL_SALE -> "일반판매";
            case HOT_DEAL -> "핫딜";
        };
    }
}
