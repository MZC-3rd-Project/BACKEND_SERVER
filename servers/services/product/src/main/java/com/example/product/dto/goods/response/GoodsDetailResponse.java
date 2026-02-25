package com.example.product.dto.goods.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.product.dto.image.response.ItemImagesResponse;
import com.example.product.dto.item.response.ItemContentSnapshot;
import com.example.product.dto.item.response.ItemDetailSectionResponse;
import com.example.product.entity.item.Item;
import com.example.product.entity.goods.ItemOption;
import com.example.product.entity.image.ItemImage;
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
    private ItemImagesResponse images;

    @SnowflakeId
    private Long categoryId;

    @SnowflakeId
    private Long sellerId;

    @SnowflakeId
    private Long storeId;

    private List<String> tags;
    private List<String> features;
    private List<ItemDetailSectionResponse> detailSections;

    private List<ItemOptionResponse> options;
    private ShippingInfoResponse shippingInfo;
    private List<Long> linkedPerformanceItemIds;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static GoodsDetailResponse of(Item item, List<ItemOption> options,
                                         ShippingInfo shippingInfo, List<Long> linkedIds,
                                         ItemContentSnapshot content, List<ItemImage> images) {
        ItemImagesResponse imageResponse = ItemImagesResponse.from(images, item.getThumbnailMediaId());
        ItemContentSnapshot safeContent = content != null ? content : ItemContentSnapshot.empty();

        return GoodsDetailResponse.builder()
                .id(item.getId())
                .title(item.getTitle())
                .description(item.getDescription())
                .price(item.getPrice())
                .status(item.getStatus().name())
                .itemType(item.getItemType().name())
                .images(imageResponse)
                .categoryId(item.getCategoryId())
                .sellerId(item.getSellerId())
                .storeId(item.getStoreId())
                .tags(safeContent.tags())
                .features(safeContent.features())
                .detailSections(safeContent.detailSections())
                .options(options.stream().map(ItemOptionResponse::from).toList())
                .shippingInfo(shippingInfo != null ? ShippingInfoResponse.from(shippingInfo) : null)
                .linkedPerformanceItemIds(linkedIds)
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
