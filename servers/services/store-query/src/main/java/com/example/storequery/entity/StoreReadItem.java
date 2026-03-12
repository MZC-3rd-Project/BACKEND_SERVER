package com.example.storequery.entity;

import com.example.data.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "store_read_items")
public class StoreReadItem extends BaseEntity {

    @Id
    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "price", nullable = false)
    private Long price;

    @Column(name = "item_type", nullable = false, length = 20)
    private String itemType;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "thumbnail_media_id")
    private Long thumbnailMediaId;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "source_updated_at", nullable = false)
    private LocalDateTime sourceUpdatedAt;

    @Column(name = "projected_at", nullable = false)
    private LocalDateTime projectedAt;

    public static StoreReadItem of(
        Long itemId,
        Long storeId,
        Long sellerId,
        String title,
        Long price,
        String itemType,
        String status,
        Long thumbnailMediaId,
        String thumbnailUrl,
        LocalDateTime sourceUpdatedAt,
        LocalDateTime projectedAt
    ) {
        return StoreReadItem.builder()
            .itemId(itemId)
            .storeId(storeId)
            .sellerId(sellerId)
            .title(title)
            .price(price)
            .itemType(itemType)
            .status(status)
            .thumbnailMediaId(thumbnailMediaId)
            .thumbnailUrl(thumbnailUrl)
            .sourceUpdatedAt(sourceUpdatedAt)
            .projectedAt(projectedAt == null ? LocalDateTime.now() : projectedAt)
            .build();
    }
}
