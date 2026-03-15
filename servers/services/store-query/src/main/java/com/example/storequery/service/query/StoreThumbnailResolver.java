package com.example.storequery.service.query;

import com.example.storequery.dto.response.StoreQueryImageResponse;
import com.example.storequery.entity.StoreQueryImageType;
import com.example.storequery.entity.StoreReadImage;
import com.example.storequery.entity.StoreReadModel;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
public class StoreThumbnailResolver {

    public StoreQueryImageResponse resolve(StoreSummarySource source) {
        if (source.thumbnailMediaId() == null) {
            return null;
        }
        return buildImage(
            source.thumbnailMediaId(),
            source.thumbnailUrl(),
            StoreQueryImageType.THUMBNAIL,
            source.thumbnailSortOrder()
        );
    }

    public StoreQueryImageResponse resolve(StoreReadModel model, List<StoreReadImage> images) {
        if (model.getThumbnailMediaId() != null) {
            return buildImage(
                model.getThumbnailMediaId(),
                model.getThumbnailUrl(),
                StoreQueryImageType.THUMBNAIL,
                model.getThumbnailSortOrder()
            );
        }

        return images.stream()
            .filter(image -> image.getImageType() == StoreQueryImageType.THUMBNAIL)
            .min(Comparator.comparing(StoreReadImage::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
            .map(this::toImage)
            .orElse(null);
    }

    public List<StoreQueryImageResponse> resolveGallery(StoreQueryImageResponse thumbnail, List<StoreReadImage> images) {
        return images.stream()
            .sorted(Comparator.comparing(StoreReadImage::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
            .filter(image -> thumbnail == null || !Objects.equals(image.getMediaId(), thumbnail.mediaId()))
            .map(this::toImage)
            .toList();
    }

    public StoreQueryImageResponse toImage(StoreReadImage image) {
        return buildImage(
            image.getMediaId(),
            image.getMediaUrl(),
            image.getImageType(),
            image.getSortOrder()
        );
    }

    private StoreQueryImageResponse buildImage(
        Long mediaId,
        String mediaUrl,
        StoreQueryImageType imageType,
        Integer sortOrder
    ) {
        return StoreQueryImageResponse.builder()
            .mediaId(mediaId)
            .mediaUrl(mediaUrl)
            .imageType(imageType)
            .sortOrder(sortOrder)
            .build();
    }
}
