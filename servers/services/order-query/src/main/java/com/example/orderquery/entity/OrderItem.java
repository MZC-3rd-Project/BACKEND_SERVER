package com.example.orderquery.entity;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import com.example.orderquery.entity.Enums.ItemType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_items",
        indexes = {
                @Index(name = "idx_order_items_order", columnList = "order_id"),
                @Index(name = "idx_order_items_item", columnList = "item_id")
        })
@Immutable
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Orders order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Items item;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type_snap", nullable = false, length = 20)
    private ItemType itemTypeSnap;

    @Column(name = "title_snap", nullable = false, length = 200)
    private String titleSnap;

    @Column(name = "price_snap", nullable = false)
    private Long priceSnap;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;
}
