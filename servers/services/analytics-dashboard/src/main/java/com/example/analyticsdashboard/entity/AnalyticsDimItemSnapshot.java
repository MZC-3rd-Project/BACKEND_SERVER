package com.example.analyticsdashboard.entity;

import com.example.data.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "analytics_dim_item_snapshot",
        indexes = {
                @Index(name = "idx_analytics_dim_item_snapshot_store", columnList = "store_id"),
                @Index(name = "idx_analytics_dim_item_snapshot_seller", columnList = "seller_id"),
                @Index(name = "idx_analytics_dim_item_snapshot_status", columnList = "item_status")
        }
)
public class AnalyticsDimItemSnapshot extends BaseEntity {

    @Id
    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "item_type", nullable = false, length = 30)
    private String itemType;

    @Column(name = "item_status", nullable = false, length = 30)
    private String itemStatus;

    @Column(name = "price")
    private Long price;

    @Column(name = "stock_quantity")
    private Long stockQuantity;

    @Column(name = "snapshot_at", nullable = false)
    private LocalDateTime snapshotAt;

    @Builder
    private AnalyticsDimItemSnapshot(
            Long itemId,
            Long storeId,
            Long sellerId,
            String itemType,
            String itemStatus,
            Long price,
            Long stockQuantity,
            LocalDateTime snapshotAt
    ) {
        this.itemId = itemId;
        this.storeId = storeId;
        this.sellerId = sellerId;
        this.itemType = itemType;
        this.itemStatus = itemStatus;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.snapshotAt = snapshotAt;
    }

    public void updateSnapshot(Long storeId,
                               String itemType,
                               String itemStatus,
                               Long price,
                               Long stockQuantity,
                               LocalDateTime snapshotAt) {
        this.storeId = storeId;
        this.itemType = itemType;
        this.itemStatus = itemStatus;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.snapshotAt = snapshotAt;
    }
}
