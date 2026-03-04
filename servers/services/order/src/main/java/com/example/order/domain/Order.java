package com.example.order.domain;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders",
        indexes = {
                @Index(name = "idx_order_user_id", columnList = "user_id"),
                @Index(name = "idx_order_purchase_id", columnList = "purchase_id", unique = true),
                @Index(name = "idx_order_status", columnList = "status")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class Order extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "purchase_id", nullable = false)
    private Long purchaseId;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "payment_id")
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    public static Order create(Long userId, Long purchaseId, Long totalAmount, Long reservationId) {
        Order order = new Order();
        order.userId = userId;
        order.purchaseId = purchaseId;
        order.totalAmount = totalAmount;
        order.reservationId = reservationId;
        order.status = OrderStatus.CREATED;
        return order;
    }

    public void addItem(OrderItem item) {
        orderItems.add(item);
        item.setOrder(this);
    }

    public void markAsPaid(Long paymentId) {
        status.validateTransitionTo(OrderStatus.PAID);
        this.status = OrderStatus.PAID;
        this.paymentId = paymentId;
    }

    public void complete() {
        status.validateTransitionTo(OrderStatus.COMPLETED);
        this.status = OrderStatus.COMPLETED;
    }

    public void cancel() {
        status.validateTransitionTo(OrderStatus.CANCELLED);
        this.status = OrderStatus.CANCELLED;
    }

    public void requestRefund() {
        status.validateTransitionTo(OrderStatus.REFUND_REQUESTED);
        this.status = OrderStatus.REFUND_REQUESTED;
    }

    public void markAsRefunded() {
        status.validateTransitionTo(OrderStatus.REFUNDED);
        this.status = OrderStatus.REFUNDED;
    }
}
