package com.example.product.controller;

import com.example.api.response.ApiResponse;
import com.example.product.controller.api.ItemImageApi;
import com.example.product.dto.image.ItemImageRequest;
import com.example.product.dto.image.ItemImageResponse;
import com.example.product.service.ItemImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemImageController implements ItemImageApi {

    private final ItemImageService itemImageService;

    @Override
    public ApiResponse<List<ItemImageResponse>> addImages(Long itemId, List<ItemImageRequest> requests) {
        return ApiResponse.success(itemImageService.addImages(itemId, requests));
    }

    @Override
    public ApiResponse<List<ItemImageResponse>> findByItemId(Long itemId) {
        return ApiResponse.success(itemImageService.findByItemId(itemId));
    }

    @Override
    public ApiResponse<Void> deleteImage(Long imageId) {
        itemImageService.deleteImage(imageId);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<List<ItemImageResponse>> reorder(Long itemId, List<Long> imageIds) {
        return ApiResponse.success(itemImageService.reorder(itemId, imageIds));
    }
}
