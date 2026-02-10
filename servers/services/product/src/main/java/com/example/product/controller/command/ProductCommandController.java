package com.example.product.controller.command;

import com.example.api.response.ApiResponse;
import com.example.product.controller.api.command.ProductCommandApi;
import com.example.product.dto.goods.request.ProductCreateRequest;
import com.example.product.dto.goods.request.ProductUpdateRequest;
import com.example.product.dto.goods.response.GoodsDetailResponse;
import com.example.product.service.command.ProductCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductCommandController implements ProductCommandApi {

    private final ProductCommandService productCommandService;

    @Override
    public ApiResponse<GoodsDetailResponse> create(ProductCreateRequest request, Long sellerId) {
        return ApiResponse.success(productCommandService.createProduct(request, sellerId));
    }

    @Override
    public ApiResponse<GoodsDetailResponse> update(Long itemId, ProductUpdateRequest request, Long sellerId) {
        return ApiResponse.success(productCommandService.updateProduct(itemId, request, sellerId));
    }

    @Override
    public ApiResponse<Void> delete(Long itemId, Long sellerId) {
        productCommandService.delete(itemId, sellerId);
        return ApiResponse.success();
    }
}
