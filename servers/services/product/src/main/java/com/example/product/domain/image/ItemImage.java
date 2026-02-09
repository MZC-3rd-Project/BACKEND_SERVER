package com.example.product.domain.image;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "item_images")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemImage extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_thumbnail", nullable = false)
    private Boolean isThumbnail;

    public static ItemImage create(Long itemId, String imageUrl, int sortOrder, boolean isThumbnail) {
        ItemImage img = new ItemImage();
        img.itemId = itemId;
        img.imageUrl = imageUrl;
        img.sortOrder = sortOrder;
        img.isThumbnail = isThumbnail;
        return img;
    }

    public void updateSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void setThumbnail(boolean isThumbnail) {
        this.isThumbnail = isThumbnail;
    }
}
