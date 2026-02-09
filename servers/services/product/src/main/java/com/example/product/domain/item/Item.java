package com.example.product.domain.item;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "items")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Item extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price", nullable = false)
    private Long price;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private ItemType itemType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ItemStatus status;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    public static Item create(String title, String description, Long price,
                              ItemType itemType, Long categoryId, Long sellerId, String thumbnailUrl) {
        Item item = new Item();
        item.title = title;
        item.description = description;
        item.price = price;
        item.itemType = itemType;
        item.status = ItemStatus.DRAFT;
        item.categoryId = categoryId;
        item.sellerId = sellerId;
        item.thumbnailUrl = thumbnailUrl;
        return item;
    }

    public void update(String title, String description, Long price, Long categoryId, String thumbnailUrl) {
        this.title = title;
        this.description = description;
        this.price = price;
        this.categoryId = categoryId;
        this.thumbnailUrl = thumbnailUrl;
    }

    public void changeStatus(ItemStatus newStatus) {
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
    }

    public boolean isOwnedBy(Long sellerId) {
        return this.sellerId.equals(sellerId);
    }
}
