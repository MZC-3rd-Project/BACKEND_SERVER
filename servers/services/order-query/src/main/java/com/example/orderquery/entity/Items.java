package com.example.orderquery.entity;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import com.example.orderquery.entity.Enums.ItemStatus;
import com.example.orderquery.entity.Enums.ItemType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "items",
        indexes = {
                @Index(name = "idx_items_user_id", columnList = "user_id"),
                @Index(name = "idx_items_status", columnList = "status")
        })
@Immutable
@SQLRestriction("deleted_at IS NULL")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Items extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "price", nullable = false)
    private Long price;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private ItemType itemType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ItemStatus status;
}
