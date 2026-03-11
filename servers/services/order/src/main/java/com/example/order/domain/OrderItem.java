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

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    @Column(name = "subtotal", nullable = false)
    private Long subtotal;

    public static OrderItem create(Long itemId, String itemName, Integer quantity, Long unitPrice) {
        OrderItem item = new OrderItem();
        item.itemId = itemId;
        item.itemName = itemName;
        item.quantity = quantity;
        item.unitPrice = unitPrice;
        item.subtotal = unitPrice * quantity;
        return item;
    }
}
