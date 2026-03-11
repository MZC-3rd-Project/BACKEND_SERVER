package com.example.order.domain;

import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders",
        indexes = {
                @Index(name = "idx_orders_user_id", columnList = "user_id"),
                @Index(name = "idx_orders_status", columnList = "status")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class Order extends BaseEntity {

    @Id
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "recipient_name", length = 100)
    private String recipientName;

    @Column(name = "recipient_phone", length = 20)
    private String recipientPhone;

    @Column(name = "delivery_address_id")
    private Long deliveryAddressId;

    @Column(name = "delivery_memo", length = 500)
    private String deliveryMemo;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    public static Order create(Long orderId, Long userId, Long totalAmount,
                               String recipientName, String recipientPhone,
                               Long deliveryAddressId, String deliveryMemo,
                               LocalDateTime expiresAt) {
        Order order = new Order();
        order.id = orderId;
        order.userId = userId;
        order.status = OrderStatus.PAYMENT_PENDING;
        order.totalAmount = totalAmount;
        order.recipientName = recipientName;
        order.recipientPhone = recipientPhone;
        order.deliveryAddressId = deliveryAddressId;
        order.deliveryMemo = deliveryMemo;
        order.expiresAt = expiresAt;
        return order;
    }

    public void addItem(OrderItem item) {
        orderItems.add(item);
        item.setOrder(this);
    }

    public void transitTo(OrderStatus target) {
        status.validateTransitionTo(target);
        this.status = target;
    }

    public void markAsPaid() {
        transitTo(OrderStatus.PAID);
    }

    public void cancel() {
        transitTo(OrderStatus.CANCELLED);
    }

    public void requestRefund() {
        transitTo(OrderStatus.REFUND_REQUESTED);
    }

    public void markAsRefunded() {
        transitTo(OrderStatus.REFUNDED);
    }

    // TODO: 배송지 변경 API - PATCH /api/v1/orders/{orderId}/delivery-address (deliveryAddressId 변경 가능해야 함)
}
