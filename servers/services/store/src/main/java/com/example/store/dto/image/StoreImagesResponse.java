package com.example.store.dto.image;

import com.example.store.entity.ImageType;
import com.example.store.entity.StoreImage;
import lombok.Builder;
import lombok.Getter;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Getter
@Builder
public class StoreImagesResponse {
    StoreImageResponse thumbnail;
    List<StoreImageResponse> gallery;

    public static StoreImagesResponse from(List<StoreImage> images) {
        return from(images, null);
    }

    /**
     * 썸네일 선택 우선순위
     * 1. preferredThumbnailMediaId와 mediaId가 일치하는 이미지
     * 2. imageType == THUMBNAIL 인 이미지
     * 3. sortOrder 기준 첫 번째 이미지
     *
     * 선택된 썸네일은 gallery에서 제외됨
     */
    public static StoreImagesResponse from(List<StoreImage> images, Long preferredThumbnailMediaId) {
        if (images == null || images.isEmpty()) {
            return StoreImagesResponse.builder()
                .thumbnail(null)
                .gallery(List.of())
                .build();
        }

        // sortOrder 오름차순, 동일하면 id 오름차순
        List<StoreImage> sorted = images.stream()
            .sorted(Comparator
                .comparing(StoreImage::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(StoreImage::getId, Comparator.nullsLast(Long::compareTo)))
            .toList();

        // 1순위: preferredThumbnailMediaId 일치
        StoreImage selectedThumbnail = sorted.stream()
            .filter(img -> preferredThumbnailMediaId != null
                && Objects.equals(preferredThumbnailMediaId, img.getMediaId()))
            .findFirst()
            .orElse(null);

        // 2순위: imageType == THUMBNAIL
        if (selectedThumbnail == null) {
            selectedThumbnail = sorted.stream()
                .filter(img -> img.getImageType() == ImageType.THUMBNAIL)
                .findFirst()
                .orElse(null);
        }

        // 3순위: 첫 번째 이미지
        if (selectedThumbnail == null) {
            selectedThumbnail = sorted.get(0);
        }

        StoreImageResponse thumbnail = StoreImageResponse.of(selectedThumbnail);
        Long selectedId = selectedThumbnail.getId();

        // 썸네일로 선택된 이미지는 gallery에서 제외
        List<StoreImageResponse> gallery = sorted.stream()
            .filter(img -> !Objects.equals(img.getId(), selectedId))
            .map(StoreImageResponse::of)
            .toList();

        return StoreImagesResponse.builder()
            .thumbnail(thumbnail)
            .gallery(gallery)
            .build();
    }

}
