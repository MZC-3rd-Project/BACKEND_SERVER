package com.example.product.dto.goods.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.product.entity.item.Item;
import com.example.product.entity.goods.ItemOption;
import com.example.product.entity.goods.ShippingInfo;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class GoodsDetailResponse {

    @SnowflakeId
    private Long id;

    private String title;
    private String description;
    private Long price;
    private String status;
    private String itemType;
    private String thumbnailUrl;

    @SnowflakeId
    private Long categoryId;

    @SnowflakeId
    private Long sellerId;

    private List<ItemOptionResponse> options;
    private ShippingInfoResponse shippingInfo;
    private List<Long> linkedPerformanceItemIds;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static GoodsDetailResponse of(Item item, List<ItemOption> options,
                                         ShippingInfo shippingInfo, List<Long> linkedIds) {
        return GoodsDetailResponse.builder()
                .id(item.getId())
                .title(item.getTitle())
                .description(item.getDescription())
                .price(item.getPrice())
                .status(item.getStatus().name())
                .itemType(item.getItemType().name())
                .thumbnailUrl(item.getThumbnailUrl())
                .categoryId(item.getCategoryId())
                .sellerId(item.getSellerId())
                .options(options.stream().map(ItemOptionResponse::from).toList())
                .shippingInfo(shippingInfo != null ? ShippingInfoResponse.from(shippingInfo) : null)
                .linkedPerformanceItemIds(linkedIds)
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
