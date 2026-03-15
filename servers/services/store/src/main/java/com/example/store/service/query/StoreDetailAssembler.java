package com.example.store.service.query;

import com.example.store.dto.image.StoreImageResponse;
import com.example.store.dto.image.StoreImagesResponse;
import com.example.store.dto.response.StoreDetailResponse;
import com.example.store.dto.response.internal.StoreSnapshotResponse;
import com.example.store.service.query.view.StoreDetailBaseView;
import com.example.store.service.query.view.StoreImageView;
import com.example.store.service.query.view.StoreSnapshotBaseView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class StoreDetailAssembler {

    private final StoreImageSelectionPolicy storeImageSelectionPolicy;

    public StoreDetailResponse toDetailResponse(StoreDetailBaseView base, List<StoreImageView> images) {
        return new StoreDetailResponse(
            base.id(),
            base.userId(),
            base.storeName(),
            base.status(),
            base.description(),
            base.address(),
            base.addressType(),
            toImagesResponse(images)
        );
    }

    public StoreSnapshotResponse toSnapshotResponse(StoreSnapshotBaseView base, List<StoreImageView> images) {
        List<StoreSnapshotResponse.StoreSnapshotImageResponse> snapshotImages = images == null
            ? List.of()
            : images.stream().map(this::toSnapshotImageResponse).toList();

        LocalDateTime sourceUpdatedAt = snapshotImages.stream()
            .map(StoreSnapshotResponse.StoreSnapshotImageResponse::sourceUpdatedAt)
            .filter(Objects::nonNull)
            .max(LocalDateTime::compareTo)
            .map(updatedAt -> {
                if (base.sourceUpdatedAt() == null) {
                    return updatedAt;
                }
                return updatedAt.isAfter(base.sourceUpdatedAt()) ? updatedAt : base.sourceUpdatedAt();
            })
            .orElse(base.sourceUpdatedAt());

        return new StoreSnapshotResponse(
            base.storeId(),
            base.userId(),
            base.storeName(),
            base.status(),
            base.description(),
            base.address(),
            base.addressType(),
            base.contactValue(),
            base.contactType(),
            snapshotImages,
            base.sourceCreatedAt(),
            sourceUpdatedAt
        );
    }

    public StoreImagesResponse toImagesResponse(List<StoreImageView> images) {
        StoreImageView thumbnail = storeImageSelectionPolicy.selectThumbnail(images);
        List<StoreImageView> gallery = storeImageSelectionPolicy.selectGallery(images, thumbnail);

        return StoreImagesResponse.builder()
            .thumbnail(toImageResponse(thumbnail))
            .gallery(gallery.stream().map(this::toImageResponse).toList())
            .build();
    }

    private StoreImageResponse toImageResponse(StoreImageView imageView) {
        if (imageView == null) {
            return null;
        }

        return StoreImageResponse.builder()
            .storeId(imageView.storeId())
            .mediaId(imageView.mediaId())
            .imageType(imageView.imageType())
            .sortOrder(imageView.sortOrder())
            .build();
    }

    private StoreSnapshotResponse.StoreSnapshotImageResponse toSnapshotImageResponse(StoreImageView imageView) {
        return new StoreSnapshotResponse.StoreSnapshotImageResponse(
            imageView.imageType(),
            imageView.mediaId(),
            imageView.sortOrder(),
            imageView.sourceUpdatedAt()
        );
    }
}
