package com.example.storequery.dto.response;

import com.example.storequery.entity.StoreQueryStatus;
import com.example.storequery.entity.StoreReadModel;
import lombok.Builder;

@Builder
public record StoreQueryListResponse(
    Long storeId,
    Long userId,
    String storeName,
    StoreQueryStatus status,
    String description,
    String contactValue,
    String address,
    String ownerNickname,
    StoreQueryImageResponse thumbnail
) {

    public static StoreQueryListResponse from(StoreReadModel model) {
        return StoreQueryListResponse.builder()
            .storeId(model.getStoreId())
            .userId(model.getUserId())
            .storeName(model.getStoreName())
            .status(model.getStatus())
            .description(model.getDescription())
            .contactValue(model.getPrimaryContactValue())
            .address(model.getDefaultAddress())
            .ownerNickname(model.getOwnerNickname())
            .thumbnail(toThumbnail(model))
            .build();
    }

    private static StoreQueryImageResponse toThumbnail(StoreReadModel model) {
        if (model.getThumbnailMediaId() == null) {
            return null;
        }
        return StoreQueryImageResponse.of(
            model.getThumbnailMediaId(),
            model.getThumbnailUrl(),
            com.example.storequery.entity.StoreQueryImageType.THUMBNAIL,
            model.getThumbnailSortOrder()
        );
    }
}
