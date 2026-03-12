package com.example.storequery.dto.response;

import com.example.storequery.entity.StoreQueryImageType;
import com.example.storequery.entity.StoreReadImage;
import lombok.Builder;

@Builder
public record StoreQueryImageResponse(
    Long mediaId,
    String mediaUrl,
    StoreQueryImageType imageType,
    Integer sortOrder
) {

    public static StoreQueryImageResponse of(StoreReadImage image) {
        return StoreQueryImageResponse.builder()
            .mediaId(image.getMediaId())
            .mediaUrl(image.getMediaUrl())
            .imageType(image.getImageType())
            .sortOrder(image.getSortOrder())
            .build();
    }

    public static StoreQueryImageResponse of(
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
