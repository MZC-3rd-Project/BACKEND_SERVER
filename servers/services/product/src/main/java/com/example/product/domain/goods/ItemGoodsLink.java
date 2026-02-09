package com.example.product.domain.goods;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "item_goods_links", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"performance_item_id", "goods_item_id"})
})
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
