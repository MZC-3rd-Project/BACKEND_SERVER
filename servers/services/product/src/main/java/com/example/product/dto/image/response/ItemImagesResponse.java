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

        ItemImageResponse thumbnail = sorted.stream()
                .filter(image -> Boolean.TRUE.equals(image.getIsThumbnail()))
                .findFirst()
                .map(ItemImageResponse::from)
                .orElse(null);

        List<ItemImageResponse> gallery = sorted.stream()
                .filter(image -> !Boolean.TRUE.equals(image.getIsThumbnail()))
                .map(ItemImageResponse::from)
                .toList();

        if (thumbnail == null && !sorted.isEmpty()) {
            ItemImage first = sorted.get(0);
            thumbnail = ItemImageResponse.from(first);
            Long fallbackThumbnailId = first.getId();
            gallery = sorted.stream()
                    .filter(image -> !Objects.equals(image.getId(), fallbackThumbnailId))
                    .map(ItemImageResponse::from)
                    .toList();
        }

        return ItemImagesResponse.builder()
                .thumbnail(thumbnail)
                .gallery(gallery)
                .build();
    }
}
