package com.example.product.service.query.assembler;

import com.example.product.dto.goods.response.GoodsDetailResponse;
import com.example.product.dto.goods.response.ItemOptionResponse;
import com.example.product.dto.goods.response.ShippingInfoResponse;
import com.example.product.dto.image.response.ItemImagesResponse;
import com.example.product.dto.item.response.ItemPriceMetaResponse;
import com.example.product.service.query.detail.GoodsItemDetailView;
import org.springframework.stereotype.Component;

@Component
public class ProductDetailAssembler {

    public GoodsDetailResponse toResponse(GoodsItemDetailView detailView) {
        return GoodsDetailResponse.builder()
                .id(detailView.item().getId())
                .title(detailView.item().getTitle())
                .description(detailView.item().getDescription())
                .price(detailView.item().getPrice())
                .status(detailView.item().getStatus().name())
                .itemType(detailView.item().getItemType().name())
                .images(ItemImagesResponse.from(detailView.images(), detailView.item().getThumbnailMediaId()))
                .averageRating(detailView.item().getAverageRating())
                .reviewCount(detailView.item().getReviewCount())
                .categoryId(detailView.item().getCategoryId())
                .categoryName(detailView.categoryDetail() != null ? detailView.categoryDetail().categoryName() : null)
                .categoryPath(detailView.categoryDetail() != null ? detailView.categoryDetail().categoryPath() : java.util.List.of())
                .sellerId(detailView.item().getSellerId())
                .storeId(detailView.item().getStoreId())
                .tags(detailView.contentSnapshot().tags())
                .features(detailView.contentSnapshot().features())
                .detailSections(detailView.contentSnapshot().detailSections())
                .priceMeta(ItemPriceMetaResponse.fromOptions(detailView.item().getPrice(), detailView.options()))
                .options(detailView.options().stream().map(ItemOptionResponse::from).toList())
                .shippingInfo(detailView.shippingInfo() != null ? ShippingInfoResponse.from(detailView.shippingInfo()) : null)
                .linkedPerformanceItemIds(detailView.linkedPerformanceItemIds())
                .linkedPerformanceItems(detailView.linkedPerformanceItems())
                .createdAt(detailView.item().getCreatedAt())
                .updatedAt(detailView.item().getUpdatedAt())
                .build();
    }
}
