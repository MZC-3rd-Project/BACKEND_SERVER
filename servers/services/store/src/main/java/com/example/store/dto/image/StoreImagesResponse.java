package com.example.store.dto.image;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class StoreImagesResponse {
    StoreImageResponse thumbnail;
    List<StoreImageResponse> gallery;
}
