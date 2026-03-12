package com.example.store.dto.response.internal;

import com.example.store.entity.AddressType;
import com.example.store.entity.ContactType;
import com.example.store.entity.ImageType;
import com.example.store.entity.StoreImage;
import com.example.store.entity.StoreStatus;

import java.time.LocalDateTime;
import java.util.List;

public record StoreSnapshotResponse(
    Long storeId,
    Long userId,
    String storeName,
    StoreStatus status,
    String description,
    String address,
    AddressType addressType,
    String contactValue,
    ContactType contactType,
    List<StoreSnapshotImageResponse> images,
    LocalDateTime sourceCreatedAt,
    LocalDateTime sourceUpdatedAt
) {

    public StoreSnapshotResponse {
        images = images == null ? List.of() : List.copyOf(images);
    }

    public StoreSnapshotResponse withImages(List<StoreSnapshotImageResponse> images) {
        return new StoreSnapshotResponse(
            storeId,
            userId,
            storeName,
            status,
            description,
            address,
            addressType,
            contactValue,
            contactType,
            images,
            sourceCreatedAt,
            sourceUpdatedAt
        );
    }

    public StoreSnapshotResponse withSourceUpdatedAt(LocalDateTime updatedAt) {
        return new StoreSnapshotResponse(
            storeId,
            userId,
            storeName,
            status,
            description,
            address,
            addressType,
            contactValue,
            contactType,
            images,
            sourceCreatedAt,
            updatedAt
        );
    }

    public record StoreSnapshotImageResponse(
        ImageType imageType,
        Long mediaId,
        Integer sortOrder,
        LocalDateTime sourceUpdatedAt
    ) {

        public static StoreSnapshotImageResponse from(StoreImage image) {
            return new StoreSnapshotImageResponse(
                image.getImageType(),
                image.getMediaId(),
                image.getSortOrder(),
                image.getUpdatedAt()
            );
        }
    }
}
