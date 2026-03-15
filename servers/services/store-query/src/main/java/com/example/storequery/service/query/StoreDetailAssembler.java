package com.example.storequery.service.query;

import com.example.storequery.dto.response.StoreQueryDetailResponse;
import com.example.storequery.dto.response.StoreQueryImageResponse;
import com.example.storequery.dto.response.StoreQueryImagesResponse;
import com.example.storequery.dto.response.StoreQueryItemSummaryResponse;
import com.example.storequery.entity.StoreReadImage;
import com.example.storequery.entity.StoreReadItem;
import com.example.storequery.entity.StoreReadModel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StoreDetailAssembler {

    private final StoreThumbnailResolver storeThumbnailResolver;

    public StoreDetailAssembler(StoreThumbnailResolver storeThumbnailResolver) {
        this.storeThumbnailResolver = storeThumbnailResolver;
    }

    public StoreQueryDetailResponse toDetailResponse(
        StoreReadModel model,
        List<StoreReadImage> images,
        List<StoreReadItem> items
    ) {
        StoreQueryImageResponse thumbnail = storeThumbnailResolver.resolve(model, images);
        List<StoreQueryImageResponse> gallery = storeThumbnailResolver.resolveGallery(thumbnail, images);

        return StoreQueryDetailResponse.builder()
            .storeId(model.getStoreId())
            .userId(model.getUserId())
            .storeName(model.getStoreName())
            .status(model.getStatus())
            .description(model.getDescription())
            .address(model.getDefaultAddress())
            .addressType(model.getDefaultAddressType())
            .contactValue(model.getPrimaryContactValue())
            .contactType(model.getPrimaryContactType())
            .ownerNickname(model.getOwnerNickname())
            .ownerProfileImageUrl(model.getOwnerProfileImageUrl())
            .activeItemCount(model.getActiveItemCount())
            .latestItemUpdatedAt(model.getLatestItemUpdatedAt())
            .images(StoreQueryImagesResponse.builder()
                .thumbnail(thumbnail)
                .gallery(gallery)
                .build())
            .items(items.stream().map(this::toItemSummary).toList())
            .build();
    }

    private StoreQueryItemSummaryResponse toItemSummary(StoreReadItem item) {
        return StoreQueryItemSummaryResponse.builder()
            .itemId(item.getItemId())
            .sellerId(item.getSellerId())
            .title(item.getTitle())
            .price(item.getPrice())
            .itemType(item.getItemType())
            .status(item.getStatus())
            .thumbnailMediaId(item.getThumbnailMediaId())
            .thumbnailUrl(item.getThumbnailUrl())
            .build();
    }
}
