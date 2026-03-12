package com.example.storequery.dto.response;

import lombok.Builder;

import java.util.List;

@Builder
public record StoreQueryImagesResponse(
    StoreQueryImageResponse thumbnail,
    List<StoreQueryImageResponse> gallery
) {
}
