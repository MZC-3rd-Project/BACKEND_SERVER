package com.example.product.entity.goods;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "item_options", indexes = {
        @Index(name = "idx_item_options_item_id", columnList = "item_id")
})
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemOption extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "option_name", nullable = false, length = 100)
    private String optionName;

    @Column(name = "additional_price", nullable = false)
    private Long additionalPrice;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    public static ItemOption create(Long itemId, String optionName, Long additionalPrice, int stockQuantity) {
        ItemOption opt = new ItemOption();
        opt.itemId = itemId;
        opt.optionName = optionName;
        opt.additionalPrice = additionalPrice;
        opt.stockQuantity = stockQuantity;
        return opt;
    }

    public void update(String optionName, Long additionalPrice, int stockQuantity) {
        this.optionName = optionName;
        this.additionalPrice = additionalPrice;
        this.stockQuantity = stockQuantity;
    }
}
