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
@Table(name = "item_features", indexes = {
        @Index(name = "idx_item_features_item_id", columnList = "item_id")
})
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemFeature extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "feature_text", nullable = false, length = 200)
    private String featureText;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    public static ItemFeature create(Long itemId, String featureText, int sortOrder) {
        ItemFeature itemFeature = new ItemFeature();
        itemFeature.itemId = itemId;
        itemFeature.featureText = featureText;
        itemFeature.sortOrder = sortOrder;
        return itemFeature;
    }
}
