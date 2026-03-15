package com.example.storequery.service.query;

import com.example.storequery.dto.response.StoreQueryListResponse;
import org.springframework.stereotype.Component;

@Component
public class StoreSummaryAssembler {

    private final StoreThumbnailResolver storeThumbnailResolver;

    public StoreSummaryAssembler(StoreThumbnailResolver storeThumbnailResolver) {
        this.storeThumbnailResolver = storeThumbnailResolver;
    }

    public StoreQueryListResponse toListResponse(StoreSummarySource source) {
        return StoreQueryListResponse.builder()
            .storeId(source.storeId())
            .userId(source.userId())
            .storeName(source.storeName())
            .status(source.status())
            .description(source.description())
            .contactValue(source.primaryContactValue())
            .address(source.defaultAddress())
            .ownerNickname(source.ownerNickname())
            .thumbnail(storeThumbnailResolver.resolve(source))
            .build();
    }
}
