package com.example.product.controller;

import com.example.api.response.ApiResponse;
import com.example.product.controller.api.GoodsApi;
import com.example.product.dto.goods.GoodsCreateRequest;
import com.example.product.dto.goods.GoodsDetailResponse;
import com.example.product.service.ItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/goods")
@RequiredArgsConstructor
public class GoodsController implements GoodsApi {

    private final ItemService itemService;

    @Override
    public ApiResponse<GoodsDetailResponse> create(GoodsCreateRequest request, Long sellerId) {
        return ApiResponse.success(itemService.createGoods(request, sellerId));
    }

    @Override
    public ApiResponse<GoodsDetailResponse> findById(Long itemId) {
        return ApiResponse.success(itemService.findGoodsById(itemId));
    }

    @Override
    public ApiResponse<List<GoodsDetailResponse>> findList(Long cursor, int size) {
        return ApiResponse.success(itemService.findGoodsList(cursor, size));
    }
}
