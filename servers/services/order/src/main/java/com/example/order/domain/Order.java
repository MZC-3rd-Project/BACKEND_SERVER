package com.example.order.domain;

import com.example.core.id.jpa.SnowflakeGenerated;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private OrderType orderType;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "payment_id")
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "recipient_name", length = 50)
    private String recipientName;

    @Column(name = "recipient_phone", length = 20)
    private String recipientPhone;

    @Column(name = "zip_code", length = 10)
    private String zipCode;

    @Column(name = "address", length = 200)
    private String address;

    @Column(name = "address_detail", length = 200)
    private String addressDetail;

    @Column(name = "delivery_memo", length = 200)
    private String deliveryMemo;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    public static Order create(Long userId, Long purchaseId, OrderType orderType,
                               Long totalAmount, Long reservationId, LocalDateTime expiresAt) {
        Order order = new Order();
        order.userId = userId;
        order.purchaseId = purchaseId;
        order.orderType = orderType;
        order.totalAmount = totalAmount;
        order.reservationId = reservationId;
        order.expiresAt = expiresAt;
        order.status = OrderStatus.CREATED;
        return order;
    }

    public void addItem(OrderItem item) {
        orderItems.add(item);
        item.setOrder(this);
    }

    public void updateShippingInfo(String recipientName, String recipientPhone,
                                   String zipCode, String address,
                                   String addressDetail, String deliveryMemo) {
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.zipCode = zipCode;
        this.address = address;
        this.addressDetail = addressDetail;
        this.deliveryMemo = deliveryMemo;
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
