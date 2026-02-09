package com.example.product.dto.goods;

import com.example.core.id.jackson.SnowflakeId;
import com.example.product.domain.goods.ItemOption;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ItemOptionResponse {

    @SnowflakeId
    private Long id;

    private String optionName;
    private Long additionalPrice;
    private Integer stockQuantity;

    public static ItemOptionResponse from(ItemOption entity) {
        return ItemOptionResponse.builder()
                .id(entity.getId())
                .optionName(entity.getOptionName())
                .additionalPrice(entity.getAdditionalPrice())
                .stockQuantity(entity.getStockQuantity())
                .build();
    }
}
