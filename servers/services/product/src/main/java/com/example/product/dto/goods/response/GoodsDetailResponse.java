package com.example.product.dto.goods.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.product.dto.image.response.ItemImagesResponse;
import com.example.product.dto.item.response.ItemContentSnapshot;
import com.example.product.dto.item.response.ItemDetailSectionResponse;
import com.example.product.dto.item.response.ItemPriceMetaResponse;
import com.example.product.dto.item.response.ItemSummaryResponse;
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
    private String categoryName;
    private List<String> categoryPath;

    @SnowflakeId
    private Long sellerId;

    @SnowflakeId
    private Long storeId;

    private List<String> tags;
    private List<String> features;
    private List<ItemDetailSectionResponse> detailSections;
    private ItemPriceMetaResponse priceMeta;

    private List<ItemOptionResponse> options;
    private ShippingInfoResponse shippingInfo;
    private List<Long> linkedPerformanceItemIds;
    private List<ItemSummaryResponse> linkedPerformanceItems;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static GoodsDetailResponse of(Item item, List<ItemOption> options,
                                         ShippingInfo shippingInfo, List<Long> linkedIds,
                                         ItemContentSnapshot content, List<ItemImage> images) {
        return of(item, options, shippingInfo, linkedIds, List.of(), null, List.of(), content, images);
    }

    public static GoodsDetailResponse of(Item item, List<ItemOption> options,
                                         ShippingInfo shippingInfo, List<Long> linkedIds,
                                         List<ItemSummaryResponse> linkedItems,
                                         String categoryName, List<String> categoryPath,
                                         ItemContentSnapshot content, List<ItemImage> images) {
        ItemImagesResponse imageResponse = ItemImagesResponse.from(images, item.getThumbnailMediaId());
        ItemContentSnapshot safeContent = content != null ? content : ItemContentSnapshot.empty();
        List<ItemSummaryResponse> safeLinkedItems = linkedItems == null ? List.of() : List.copyOf(linkedItems);
        List<String> safeCategoryPath = categoryPath == null ? List.of() : List.copyOf(categoryPath);

        return GoodsDetailResponse.builder()
                .id(item.getId())
                .title(item.getTitle())
                .description(item.getDescription())
                .price(item.getPrice())
                .status(item.getStatus().name())
                .itemType(item.getItemType().name())
                .images(imageResponse)
                .categoryId(item.getCategoryId())
                .categoryName(categoryName)
                .categoryPath(safeCategoryPath)
                .sellerId(item.getSellerId())
                .storeId(item.getStoreId())
                .tags(safeContent.tags())
                .features(safeContent.features())
                .detailSections(safeContent.detailSections())
                .priceMeta(ItemPriceMetaResponse.fromOptions(item.getPrice(), options))
                .options(options.stream().map(ItemOptionResponse::from).toList())
                .shippingInfo(shippingInfo != null ? ShippingInfoResponse.from(shippingInfo) : null)
                .linkedPerformanceItemIds(linkedIds)
                .linkedPerformanceItems(safeLinkedItems)
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
