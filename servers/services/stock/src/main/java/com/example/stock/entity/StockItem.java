package com.example.stock.entity;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "stock_items", indexes = {
        @Index(name = "idx_stock_item_ref", columnList = "itemId, stockItemType, referenceId", unique = true)
})
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockItem extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(nullable = false)
    private Long itemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockItemType stockItemType;

    @Column(nullable = false)
    private Long referenceId;

    @Column(nullable = false)
    private int totalQuantity;

    @Column(nullable = false)
    private int availableQuantity;

    @Column(nullable = false)
    private int reservedQuantity;

    public static StockItem create(Long itemId, StockItemType stockItemType, Long referenceId, int totalQuantity) {
        StockItem stockItem = new StockItem();
        stockItem.itemId = itemId;
        stockItem.stockItemType = stockItemType;
        stockItem.referenceId = referenceId;
        stockItem.totalQuantity = totalQuantity;
        stockItem.availableQuantity = totalQuantity;
        stockItem.reservedQuantity = 0;
        return stockItem;
    }

    public void decrease(int quantity) {
        if (this.availableQuantity < quantity) {
            throw new IllegalStateException("Insufficient stock");
        }
        this.availableQuantity -= quantity;
    }

    public void increase(int quantity) {
        this.availableQuantity += quantity;
        if (this.availableQuantity > this.totalQuantity) {
            this.availableQuantity = this.totalQuantity;
        }
    }

    public void reserve(int quantity) {
        if (this.availableQuantity < quantity) {
            throw new IllegalStateException("Insufficient stock for reservation");
        }
        this.availableQuantity -= quantity;
        this.reservedQuantity += quantity;
    }

    public void confirmReservation(int quantity) {
        this.reservedQuantity -= quantity;
    }

    public void cancelReservation(int quantity) {
        this.reservedQuantity -= quantity;
        this.availableQuantity += quantity;
    }

    public void updateTotal(int totalQuantity) {
        int diff = totalQuantity - this.totalQuantity;
        this.totalQuantity = totalQuantity;
        this.availableQuantity += diff;
        if (this.availableQuantity < 0) this.availableQuantity = 0;
    }

    public boolean isDepleted() {
        return this.availableQuantity == 0;
    }

    public boolean isThresholdReached(double thresholdPercent) {
        if (this.totalQuantity == 0) return false;
        return ((double) this.availableQuantity / this.totalQuantity) <= thresholdPercent;
    }
}
