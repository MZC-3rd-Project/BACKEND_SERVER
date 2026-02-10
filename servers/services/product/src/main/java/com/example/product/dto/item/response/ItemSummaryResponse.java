package com.example.product.dto.item.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.product.entity.item.Item;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ItemSummaryResponse {

    @SnowflakeId
    private Long id;

    private String title;
    private Long price;
    private String itemType;
    private String status;
    private String thumbnailUrl;

    @SnowflakeId
    private Long sellerId;

    public static ItemSummaryResponse from(Item entity) {
        return ItemSummaryResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .price(entity.getPrice())
                .itemType(entity.getItemType().name())
                .status(entity.getStatus().name())
                .thumbnailUrl(entity.getThumbnailUrl())
                .sellerId(entity.getSellerId())
                .build();
    }
}
