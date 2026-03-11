package com.example.order.domain;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "order_items",
        indexes = {
                @Index(name = "idx_order_item_order_id", columnList = "order_id"),
                @Index(name = "idx_order_item_item_id", columnList = "item_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class OrderItem extends BaseEntity {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 30)
    private ItemType itemType;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "seller_id")
    private Long sellerId;

    @Column(name = "store_id")
    private Long storeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stock_item_type", length = 30)
    private StockItemType stockItemType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "reference_name", length = 100)
    private String referenceName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "base_unit_price", nullable = false)
    private Long baseUnitPrice;

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    @Column(name = "subtotal", nullable = false)
    private Long subtotal;

    public static OrderItem create(ChannelType channelType, Long channelRefId,
                                   Long itemId, ItemType itemType, String itemName,
                                   Long sellerId, Long storeId,
                                   StockItemType stockItemType, Long referenceId, String referenceName,
                                   Integer quantity, Long baseUnitPrice, Long finalUnitPrice) {
        OrderItem item = new OrderItem();
        item.channelType = channelType;
        item.channelRefId = channelRefId;
        item.itemId = itemId;
        item.itemType = itemType;
        item.itemName = itemName;
        item.sellerId = sellerId;
        item.storeId = storeId;
        item.stockItemType = stockItemType;
        item.referenceId = referenceId;
        item.referenceName = referenceName;
        item.quantity = quantity;
        item.baseUnitPrice = baseUnitPrice;
        item.unitPrice = finalUnitPrice;
        item.subtotal = finalUnitPrice * quantity;
        return item;
    }
}
