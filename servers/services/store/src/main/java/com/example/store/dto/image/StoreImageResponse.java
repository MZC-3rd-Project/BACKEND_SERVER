package com.example.store.dto.image;

import com.example.core.id.jackson.SnowflakeId;
import com.example.store.entity.ImageType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StoreImageResponse {
    @SnowflakeId
    private Long storeId;

    @SnowflakeId
    private Long mediaId;

    private ImageType imageType;
    private Integer sortOrder;
}
