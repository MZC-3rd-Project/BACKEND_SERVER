package com.example.chat.entity.room;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_rooms", indexes = {
        @Index(name = "idx_chat_rooms_room_key", columnList = "room_key"),
        @Index(name = "idx_chat_rooms_campaign_id", columnList = "campaign_id"),
        @Index(name = "idx_chat_rooms_seller_type_status", columnList = "seller_id, room_type, status")
})
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "room_key", nullable = false, length = 190)
    private String roomKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 30)
    private ChatRoomType roomType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ChatRoomStatus status;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "campaign_id")
    private Long campaignId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "read_only_at")
    private LocalDateTime readOnlyAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "read_only_reason", length = 50)
    private ChatRoomReadOnlyReason readOnlyReason;

    public static ChatRoom createInquiryRoom(String roomKey, Long itemId, Long sellerId, String title) {
        ChatRoom room = new ChatRoom();
        room.roomKey = roomKey;
        room.roomType = ChatRoomType.INQUIRY_1TO1;
        room.status = ChatRoomStatus.OPEN;
        room.itemId = itemId;
        room.sellerId = sellerId;
        room.title = title;
        return room;
    }

    public static ChatRoom createFundingGroupRoom(String roomKey, Long campaignId, Long sellerId, String title) {
        ChatRoom room = new ChatRoom();
        room.roomKey = roomKey;
        room.roomType = ChatRoomType.FUNDING_GROUP;
        room.status = ChatRoomStatus.OPEN;
        room.campaignId = campaignId;
        room.sellerId = sellerId;
        room.title = title;
        return room;
    }

    public boolean isReadOnly() {
        return status == ChatRoomStatus.READ_ONLY || status == ChatRoomStatus.ARCHIVED;
    }

    public void markReadOnly(ChatRoomReadOnlyReason reason) {
        this.status = ChatRoomStatus.READ_ONLY;
        this.readOnlyReason = reason;
        this.readOnlyAt = LocalDateTime.now();
    }

    public void archive() {
        this.status = ChatRoomStatus.ARCHIVED;
        if (this.readOnlyAt == null) {
            this.readOnlyAt = LocalDateTime.now();
        }
        if (this.readOnlyReason == null) {
            this.readOnlyReason = ChatRoomReadOnlyReason.MANUAL_ARCHIVE;
        }
    }
}
