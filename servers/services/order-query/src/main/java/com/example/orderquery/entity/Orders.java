package com.example.orderquery.entity;

import com.example.data.entity.BaseEntity;
import com.example.orderquery.entity.Enums.OrderSourceType;
import com.example.orderquery.entity.Enums.OrderStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders",
        indexes = {
                @Index(name = "idx_orders_user_id", columnList = "user_id"),
                @Index(name = "idx_orders_status", columnList = "status")
        })
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Orders extends BaseEntity {

    @Id
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private OrderSourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "recipient_name", length = 100)
    private String recipientName;

    @Column(name = "recipient_phone", length = 20)
    private String recipientPhone;

    @Column(name = "delivery_memo", length = 500)
    private String deliveryMemo;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    public static Orders create(
            Long id,
            Long userId,
            OrderSourceType sourceType,
            OrderStatus status,
            Long totalAmount,
            String recipientName,
            String recipientPhone,
            String deliveryMemo,
            LocalDateTime expiresAt
    ) {
        Orders orders = new Orders();
        orders.id = id;
        orders.userId = userId;
        orders.sourceType = sourceType;
        orders.status = status;
        orders.totalAmount = totalAmount;
        orders.recipientName = recipientName;
        orders.recipientPhone = recipientPhone;
        orders.deliveryMemo = deliveryMemo;
        orders.expiresAt = expiresAt;
        return orders;
    }

    public void updateStatus(OrderStatus status) {
        this.status = status;
    }
}
