package com.example.storequery.dto.response;

import com.example.storequery.entity.StoreQueryImageType;
import lombok.Builder;

@Builder
public record StoreQueryImageResponse(
    Long mediaId,
    String mediaUrl,
    StoreQueryImageType imageType,
    Integer sortOrder
) {
}
