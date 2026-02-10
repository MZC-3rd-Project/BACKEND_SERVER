package com.example.product.controller.command;

import com.example.api.response.ApiResponse;
import com.example.product.controller.api.command.ItemImageCommandApi;
import com.example.product.dto.image.request.ItemImageRequest;
import com.example.product.dto.image.response.ItemImageResponse;
import com.example.product.service.command.ItemImageCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemImageCommandController implements ItemImageCommandApi {

    private final ItemImageCommandService itemImageCommandService;

    @Override
    public ApiResponse<List<ItemImageResponse>> addImages(Long itemId, List<ItemImageRequest> requests, Long userId) {
        return ApiResponse.success(itemImageCommandService.addImages(itemId, requests, userId));
    }

    @Override
    public ApiResponse<Void> deleteImage(Long imageId, Long userId) {
        itemImageCommandService.deleteImage(imageId, userId);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<List<ItemImageResponse>> reorder(Long itemId, List<Long> imageIds, Long userId) {
        return ApiResponse.success(itemImageCommandService.reorder(itemId, imageIds, userId));
    }
}
