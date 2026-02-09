package com.example.product.controller;

import com.example.api.response.ApiResponse;
import com.example.product.controller.api.ProductItemApi;
import com.example.product.dto.goods.GoodsDetailResponse;
import com.example.product.dto.goods.ProductCreateRequest;
import com.example.product.service.ItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductItemController implements ProductItemApi {

    private final ItemService itemService;

    @Override
    public ApiResponse<GoodsDetailResponse> create(ProductCreateRequest request, Long sellerId) {
        return ApiResponse.success(itemService.createProduct(request, sellerId));
    }

    @Override
    public ApiResponse<GoodsDetailResponse> findById(Long itemId) {
        return ApiResponse.success(itemService.findProductById(itemId));
    }

    @Override
    public ApiResponse<List<GoodsDetailResponse>> findList(Long cursor, int size) {
        return ApiResponse.success(itemService.findProductList(cursor, size));
    }
}
