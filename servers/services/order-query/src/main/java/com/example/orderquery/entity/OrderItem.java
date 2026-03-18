package com.example.orderquery.entity;

import com.example.data.entity.BaseEntity;
import com.example.orderquery.entity.Enums.ItemType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "order_items",
        indexes = {
                @Index(name = "idx_order_items_order", columnList = "order_id"),
                @Index(name = "idx_order_items_item", columnList = "item_id"),
                @Index(name = "idx_order_items_store", columnList = "store_id")
        })
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseEntity {

    @Id
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "store_name_snap", length = 100)
    private String storeNameSnap;

    @Column(name = "channel_type", length = 20)
    private String channelType;

    @Column(name = "channel_ref_id")
    private Long channelRefId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type_snap", nullable = false, length = 20)
    private ItemType itemTypeSnap;

    @Column(name = "title_snap", nullable = false, length = 200)
    private String titleSnap;

    @Column(name = "price_snap", nullable = false)
    private Long priceSnap;

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    @Column(name = "line_amount", nullable = false)
    private Long lineAmount;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    public static OrderItem create(
            Long id,
            Long orderId,
            Long itemId,
            Long storeId,
            String channelType,
            Long channelRefId,
            ItemType itemTypeSnap,
            String titleSnap,
            Long priceSnap,
            Long unitPrice,
            Long lineAmount,
            Integer quantity
    ) {
        OrderItem item = new OrderItem();
        item.id = id;
        item.orderId = orderId;
        item.itemId = itemId;
        item.storeId = storeId;
        item.channelType = channelType;
        item.channelRefId = channelRefId;
        item.itemTypeSnap = itemTypeSnap;
        item.titleSnap = titleSnap;
        item.priceSnap = priceSnap;
        item.unitPrice = unitPrice;
        item.lineAmount = lineAmount;
        item.quantity = quantity;
        return item;
    }

    public void updateStoreNameSnap(String storeName) {
        this.storeNameSnap = storeName;
    }

    public void updateThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }
}
