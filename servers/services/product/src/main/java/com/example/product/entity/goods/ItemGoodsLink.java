package com.example.product.entity.goods;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "item_goods_links",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"performance_item_id", "goods_item_id"})
        },
        indexes = {
                @Index(name = "idx_item_goods_links_performance_item_id", columnList = "performance_item_id"),
                @Index(name = "idx_item_goods_links_goods_item_id", columnList = "goods_item_id")
        }
)
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemGoodsLink extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "performance_item_id", nullable = false)
    private Long performanceItemId;

    @Column(name = "goods_item_id", nullable = false)
    private Long goodsItemId;

    public static ItemGoodsLink create(Long performanceItemId, Long goodsItemId) {
        ItemGoodsLink link = new ItemGoodsLink();
        link.performanceItemId = performanceItemId;
        link.goodsItemId = goodsItemId;
        return link;
    }
}
