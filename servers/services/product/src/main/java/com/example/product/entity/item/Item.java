package com.example.product.entity.item;

import com.example.core.exception.BusinessException;
import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import com.example.product.exception.ProductErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "items", indexes = {
        @Index(name = "idx_items_seller_id", columnList = "seller_id"),
        @Index(name = "idx_items_category_id", columnList = "category_id"),
        @Index(name = "idx_items_item_type", columnList = "item_type"),
        @Index(name = "idx_items_status", columnList = "status")
})
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
        if (title != null) this.title = title;
        if (description != null) this.description = description;
        if (price != null) this.price = price;
        if (categoryId != null) this.categoryId = categoryId;
        if (thumbnailUrl != null) this.thumbnailUrl = thumbnailUrl;
    }

    public void changeStatus(ItemStatus newStatus) {
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
    }

    public boolean isOwnedBy(Long sellerId) {
        return this.sellerId.equals(sellerId);
    }

    public void validateOwnership(Long sellerId) {
        if (!isOwnedBy(sellerId)) {
            throw new BusinessException(ProductErrorCode.UNAUTHORIZED_ACCESS);
        }
    }

    public boolean isEditable() {
        return this.status == ItemStatus.DRAFT;
    }

    public boolean isDeletable() {
        return switch (this.status) {
            case FUNDING, FUNDED, ON_SALE, HOT_DEAL -> false;
            default -> true;
        };
    }
}
