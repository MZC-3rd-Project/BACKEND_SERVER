package com.example.store.service.query;

import com.example.store.entity.ImageType;
import com.example.store.service.query.view.StoreImageView;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
public class StoreImageSelectionPolicy {

    public List<StoreImageView> sort(List<StoreImageView> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }

        return images.stream()
            .sorted(Comparator
                .comparing(StoreImageView::sortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(StoreImageView::imageId, Comparator.nullsLast(Long::compareTo)))
            .toList();
    }

    public StoreImageView selectThumbnail(List<StoreImageView> images) {
        List<StoreImageView> sorted = sort(images);
        if (sorted.isEmpty()) {
            return null;
        }

        return sorted.stream()
            .filter(image -> image.imageType() == ImageType.THUMBNAIL)
            .findFirst()
            .orElse(sorted.getFirst());
    }

    public List<StoreImageView> selectGallery(List<StoreImageView> images, StoreImageView thumbnail) {
        List<StoreImageView> sorted = sort(images);
        if (sorted.isEmpty()) {
            return List.of();
        }

        if (thumbnail == null) {
            return sorted;
        }

        return sorted.stream()
            .filter(image -> !Objects.equals(image.imageId(), thumbnail.imageId()))
            .toList();
    }
}
