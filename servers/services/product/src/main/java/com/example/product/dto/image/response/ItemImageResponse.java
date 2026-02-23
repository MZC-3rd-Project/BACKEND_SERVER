package com.example.product.dto.image.response;

import com.example.core.id.jackson.SnowflakeId;
import com.example.product.entity.image.ItemImage;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ItemImageResponse {

    @SnowflakeId
    private Long id;

    @SnowflakeId
    private Long mediaId;

    private Integer sortOrder;
    private Boolean isThumbnail;

    public static ItemImageResponse from(ItemImage entity) {
        return ItemImageResponse.builder()
                .id(entity.getId())
                .mediaId(entity.getMediaId())
                .sortOrder(entity.getSortOrder())
                .isThumbnail(entity.getIsThumbnail())
                .build();
    }
}
