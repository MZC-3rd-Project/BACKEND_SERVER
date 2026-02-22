package com.example.product.dto.item.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.product.dto.image.response.ItemImagesResponse;
import com.example.product.entity.item.Item;
import com.example.product.entity.image.ItemImage;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ItemSummaryResponse {

    @SnowflakeId
    private Long id;

    private String title;
    private Long price;
    private String itemType;
    private String status;
    private ItemImagesResponse images;

    @SnowflakeId
    private Long sellerId;

    @SnowflakeId
    private Long storeId;

    public static ItemSummaryResponse from(Item entity, List<ItemImage> images) {
        return ItemSummaryResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .price(entity.getPrice())
                .itemType(entity.getItemType().name())
                .status(entity.getStatus().name())
                .images(ItemImagesResponse.from(images))
                .sellerId(entity.getSellerId())
                .storeId(entity.getStoreId())
                .build();
    }
}
