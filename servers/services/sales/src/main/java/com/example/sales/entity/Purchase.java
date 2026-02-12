package com.example.sales.entity;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "purchases",
        indexes = {
                @Index(name = "idx_purchase_user_id", columnList = "userId"),
                @Index(name = "idx_purchase_item_id", columnList = "itemId"),
                @Index(name = "idx_purchase_order_id", columnList = "orderId", unique = true),
                @Index(name = "idx_purchase_status", columnList = "status")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class Purchase extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "stock_item_id", nullable = false)
    private Long stockItemId;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "payment_id")
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PurchaseStatus status;

    public static Purchase create(Long userId, Long itemId, Long stockItemId,
                                   Long referenceId, Integer quantity,
                                   Long unitPrice, Long totalAmount,
                                   Long orderId, Long reservationId) {
        Purchase purchase = new Purchase();
        purchase.userId = userId;
        purchase.itemId = itemId;
        purchase.stockItemId = stockItemId;
        purchase.referenceId = referenceId;
        purchase.quantity = quantity;
        purchase.unitPrice = unitPrice;
        purchase.totalAmount = totalAmount;
        purchase.orderId = orderId;
        purchase.reservationId = reservationId;
        purchase.status = PurchaseStatus.RESERVED;
        return purchase;
    }

    public void changeStatus(PurchaseStatus newStatus) {
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
    }

    public void confirm(Long paymentId) {
        changeStatus(PurchaseStatus.CONFIRMED);
        this.paymentId = paymentId;
    }

    public void complete() {
        changeStatus(PurchaseStatus.COMPLETED);
    }

    public void cancel() {
        changeStatus(PurchaseStatus.CANCELLED);
    }

    public void refund() {
        changeStatus(PurchaseStatus.REFUNDED);
    }
}
