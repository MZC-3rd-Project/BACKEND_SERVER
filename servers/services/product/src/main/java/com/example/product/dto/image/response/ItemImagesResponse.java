package com.example.product.dto.image.response;

import com.example.product.entity.image.ItemImage;
import lombok.Builder;
import lombok.Getter;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Getter
@Builder
public class ItemImagesResponse {

    private ItemImageResponse thumbnail;
    private List<ItemImageResponse> gallery;

    public static ItemImagesResponse from(List<ItemImage> images) {
        return from(images, null);
    }

    public static ItemImagesResponse from(List<ItemImage> images, Long preferredThumbnailMediaId) {
        if (images == null || images.isEmpty()) {
            return ItemImagesResponse.builder()
                    .thumbnail(null)
                    .gallery(List.of())
                    .build();
        }

        List<ItemImage> sorted = images.stream()
                .sorted(Comparator
                        .comparing(ItemImage::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ItemImage::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();

        ItemImage selectedThumbnail = sorted.stream()
                .filter(image -> preferredThumbnailMediaId != null && Objects.equals(preferredThumbnailMediaId, image.getMediaId()))
                .findFirst()
                .orElse(null);
        if (selectedThumbnail == null) {
            selectedThumbnail = sorted.stream()
                    .filter(image -> Boolean.TRUE.equals(image.getIsThumbnail()))
                    .findFirst()
                    .orElse(null);
        }
        if (selectedThumbnail == null && !sorted.isEmpty()) {
            selectedThumbnail = sorted.get(0);
        }

        ItemImageResponse thumbnail = selectedThumbnail != null ? ItemImageResponse.from(selectedThumbnail) : null;
        Long selectedThumbnailId = selectedThumbnail != null ? selectedThumbnail.getId() : null;

        List<ItemImageResponse> gallery = sorted.stream()
                .filter(image -> selectedThumbnailId == null || !Objects.equals(image.getId(), selectedThumbnailId))
                .map(ItemImageResponse::from)
                .toList();

        return ItemImagesResponse.builder()
                .thumbnail(thumbnail)
                .gallery(gallery)
                .build();
    }
}
