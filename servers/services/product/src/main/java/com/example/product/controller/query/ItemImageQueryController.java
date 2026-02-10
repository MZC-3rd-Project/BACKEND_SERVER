package com.example.product.controller.query;

import com.example.api.response.ApiResponse;
import com.example.product.controller.api.query.ItemImageQueryApi;
import com.example.product.dto.image.response.ItemImageResponse;
import com.example.product.service.query.ItemImageQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemImageQueryController implements ItemImageQueryApi {

    private final ItemImageQueryService itemImageQueryService;

    @Override
    public ApiResponse<List<ItemImageResponse>> findByItemId(Long itemId) {
        return ApiResponse.success(itemImageQueryService.findByItemId(itemId));
    }
}
