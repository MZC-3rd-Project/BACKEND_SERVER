package com.example.order.domain;

import com.example.core.id.jpa.SnowflakeGenerated;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "order_items",
        indexes = {
                @Index(name = "idx_order_items_order_id", columnList = "order_id"),
                @Index(name = "idx_order_items_item_id", columnList = "item_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Setter(AccessLevel.PACKAGE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 20)
    private ChannelType channelType;

    @Column(name = "channel_ref_id")
    private Long channelRefId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    @Column(name = "line_amount", nullable = false)
    private Long lineAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private java.time.LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private java.time.LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = java.time.LocalDateTime.now();
        this.updatedAt = java.time.LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = java.time.LocalDateTime.now();
    }

    public static OrderItem create(ChannelType channelType, Long channelRefId,
                                   Long itemId, Long storeId,
                                   Integer quantity, Long unitPrice, Long lineAmount) {
        OrderItem item = new OrderItem();
        item.channelType = channelType;
        item.channelRefId = channelRefId;
        item.itemId = itemId;
        item.storeId = storeId;
        item.quantity = quantity;
        item.unitPrice = unitPrice;
        item.lineAmount = lineAmount;
        return item;
    }
}
