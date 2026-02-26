package com.example.product.entity.item;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "item_tags", indexes = {
        @Index(name = "idx_item_tags_item_id", columnList = "item_id")
})
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemTag extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "tag_name", nullable = false, length = 50)
    private String tagName;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    public static ItemTag create(Long itemId, String tagName, int sortOrder) {
        ItemTag itemTag = new ItemTag();
        itemTag.itemId = itemId;
        itemTag.tagName = tagName;
        itemTag.sortOrder = sortOrder;
        return itemTag;
    }
}
